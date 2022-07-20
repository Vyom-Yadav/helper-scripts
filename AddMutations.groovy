import groovy.transform.EqualsAndHashCode
import groovy.transform.Immutable
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.NodeChildren
import groovy.io.FileType
import groovy.transform.Field
import groovy.xml.XmlParser
import groovy.xml.XmlSlurper
import groovy.xml.XmlUtil

@Field final static String SEPARATOR = System.getProperty("file.separator")

if (args.length == 2) {
    parseArgumentAndExecute(args[0], args[1])
}
else {
    parseArgumentAndExecute(args[0], null)
}
/**
 * Parse the command line arguments passed in and execute the branch based on the arguments.
 *
 * @param argument command line argument
 */
private void parseArgumentAndExecute(String argument, String flag) {
    final Set<String> profiles = getPitestProfiles()
    final String usageString = """
        Usage groovy AddMutations.groovy [profile] [-g | --generate-suppression]
        To see the full list of supported profiles run
        'groovy AddMutations.groovy --list'
        """.stripIndent()

    if (profiles.contains(argument)) {
        if (flag != null && flag != "-g" && flag != "--generate-suppression") {
            final String exceptionMessage = "\nUnexpected flag: ${flag}" + usageString
            throw new IllegalArgumentException(exceptionMessage)
        }
        modifyPomPitestProfile(argument)
        generatePitestReport(argument)
        addSuppressions(argument, flag)
        savePitReport(argument)
        pushReportToGithub(argument)
        updateCoverageAndMutationThreshold(argument)
        makeFinalPrToCheckstyle(argument)
    }
    else if (argument == "--list") {
        println "Supported profiles:"
        profiles.each { println it }
    }
    else {
        final String exceptionMessage = "\nUnexpected argument: ${argument}" + usageString
        throw new IllegalArgumentException(exceptionMessage)
    }
}

/**
 * Parse the pom.xml file to get all the available pitest profiles.
 *
 * @return A set of all available pitest profiles
 */
private static Set<String> getPitestProfiles() {
    final GPathResult mainNode = new XmlSlurper().parse(".${SEPARATOR}pom.xml")
    final NodeChildren ids = mainNode.profiles.profile.id as NodeChildren
    final Set<String> profiles = new HashSet<>()
    ids.each { node ->
        final GPathResult id = node as GPathResult
        final String idText = id.text()
        if (idText.startsWith("pitest-")) {
            profiles.add(idText)
        }
    }
    return profiles
}

/**
 * Modify the pom.xml file to add mutators to the pitest profile.
 *
 * @param profile
 */
private static void modifyPomPitestProfile(String profile) {
    println("Modifying pom.xml of ${profile}")
    final String command = '/home/vyom/IdeaProjects/helper-scripts/modifyPomPitestProfile.sh ' + profile
    final Process proc = command.execute()
    proc.in.eachLine { println it }
    proc.waitFor()
}

/**
 * Generate the pitest report by running the respective maven command.
 *
 * @param profile the pitest profile to execute
 */
private static void generatePitestReport(String profile) {
    println "Report generation started....."
    final String command = "/home/vyom/IdeaProjects/helper-scripts/generatePitestReport.sh ${profile}"
    final Process proc = command.execute()
    proc.in.eachLine { println it }
    proc.waitFor()
}

/**
 * Add suppressions to the pitest report.
 *
 * @param profile the pitest profile
 * @param flag flag
 */
private static void addSuppressions(String profile, String flag) {
    final XmlParser xmlParser = new XmlParser()
    File mutationReportFile = null
    final String suppressedMutationFileUri =
            ".${SEPARATOR}.ci${SEPARATOR}pitest-suppressions${SEPARATOR}${profile}-suppressions.xml"

    final File pitReports = new File(".${SEPARATOR}target${SEPARATOR}pit-reports")

    if (!pitReports.exists()) {
        throw new IllegalStateException(
                "Pitest report directory does not exist, generate pitest report first")
    }

    pitReports.eachFileRecurse(FileType.FILES) {
        if (it.name == 'mutations.xml') {
            mutationReportFile = it
        }
    }
    final Node mutationReportNode = xmlParser.parse(mutationReportFile)
    final Set<Mutation> survivingMutations = getSurvivingMutations(mutationReportNode)

    if (!survivingMutations.isEmpty()) {
        final File suppressionFile = new File(suppressedMutationFileUri)
        if (suppressionFile.exists()) {
            new FileOutputStream(suppressionFile).close()
        }
        else {
            suppressionFile.createNewFile()
        }
        try (FileWriter fileWriter = new FileWriter(suppressionFile)) {
            fileWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            fileWriter.write("<suppressedMutations>")
            survivingMutations.each {
                fileWriter.write(getMutation(flag, it))
            }
            fileWriter.write("</suppressedMutations>\n")
        }
    }
}

private static void savePitReport(String profile) {
    String subNameCommand = "ls target/pit-reports"
    final Process name = subNameCommand.execute()
    String subName = null
    name.in.eachLine {
        subName = it
    }
    String copyContent = "cp -R target/pit-reports/${subName} /home/vyom/Desktop/pitest-all-latest/"
    println(copyContent)
    Process process1 = copyContent.execute()
    process1.waitFor()

    String rename = "mv /home/vyom/Desktop/pitest-all-latest/${subName} /home/vyom/Desktop/pitest-all-latest/${profile}/"
    println(rename)
    Process proc2 = rename.execute()
    proc2.waitFor()
}

private static void pushReportToGithub(String profile) {
    String pushCommand = "/home/vyom/IdeaProjects/helper-scripts/pushReportToGithub.sh ${profile}"
    Process proc = pushCommand.execute()
    proc.in.eachLine { println it }
    proc.waitFor()
}

private static void updateCoverageAndMutationThreshold(String profile) {
    String updateCommand = "/home/vyom/IdeaProjects/helper-scripts/readAndUpdateThreshold.sh ${profile}"
    Process proc = updateCommand.execute()
    proc.in.eachLine { println it }
    proc.waitFor()
}

private static void makeFinalPrToCheckstyle(String profile) {
    String makeFinalPrCommand = "/home/vyom/IdeaProjects/helper-scripts/makePR.sh ${profile}"
    Process proc = makeFinalPrCommand.execute()
    proc.in.eachLine { println it }
    proc.waitFor()
}

/**
 * Get the surviving mutations. All child nodes of the main {@code mutations} node
 * are parsed.
 *
 * @param mainNode the main {@code mutations} node
 * @return A set of surviving mutations
 */
private static Set<Mutation> getSurvivingMutations(Node mainNode) {

    final List<Node> children = mainNode.children()
    final Set<Mutation> survivingMutations = new TreeSet<>()

    children.each { node ->
        final Node mutationNode = node as Node

        final String mutationStatus = mutationNode.attribute("status")

        if (mutationStatus == "SURVIVED" || mutationStatus == "NO_COVERAGE") {
            survivingMutations.add(getMutation(mutationNode))
        }
    }
    return survivingMutations
}


/**
 * Construct the {@link Mutation} object from the {@code mutation} XML node.
 * The {@code mutations.xml} file is parsed to get the {@code mutationNode}.
 *
 * @param mutationNode the {@code mutation} XML node
 * @return {@link Mutation} object represented by the {@code mutation} XML node
 */
private static Mutation getMutation(Node mutationNode) {
    final List childNodes = mutationNode.children()

    String sourceFile = null
    String mutatedClass = null
    String mutatedMethod = null
    String mutator = null
    String lineContent = null
    String description = null
    String mutationClassPackage = null
    int lineNumber = 0
    childNodes.each {
        final Node childNode = it as Node
        final String text = childNode.name()

        final String childNodeText = XmlUtil.escapeXml(childNode.text())
        switch (text) {
            case "sourceFile":
                sourceFile = childNodeText
                break
            case "mutatedClass":
                mutatedClass = childNodeText
                mutationClassPackage = mutatedClass.split("[A-Z]")[0]
                break
            case "mutatedMethod":
                mutatedMethod = childNodeText
                break
            case "mutator":
                mutator = childNodeText
                break
            case "description":
                description = childNodeText
                break
            case "lineNumber":
                lineNumber = Integer.parseInt(childNodeText)
                break
            case "lineContent":
                lineContent = childNodeText
                break
        }
    }
    if (lineContent == null) {
        final String mutationFileName = mutationClassPackage + sourceFile
        final String startingPath = ".${SEPARATOR}src${SEPARATOR}main${SEPARATOR}java${SEPARATOR}"
        final String javaExtension = ".java"
        final String mutationFilePath = startingPath + mutationFileName
                .substring(0, mutationFileName.length() - javaExtension.length())
                .replaceAll("\\.", SEPARATOR) + javaExtension

        final File file = new File(mutationFilePath)
        lineContent = XmlUtil.escapeXml(file.readLines().get(lineNumber - 1).trim())
    }
    if (lineNumber == 0) {
        lineNumber = -1
    }

    final String unstableAttributeValue = mutationNode.attribute("unstable")
    final boolean isUnstable = Boolean.parseBoolean(unstableAttributeValue)

    return new Mutation(sourceFile, mutatedClass, mutatedMethod, mutator, description,
            lineContent, lineNumber, isUnstable)
}

/**
 * Prints the mutation according to the nature of the flag.
 *
 * @param flag command line argument flag to determine output format
 * @param mutation mutation to print
 */
private static String getMutation(String flag, Mutation mutation) {
    if (flag != null) {
        return mutation.toXmlString()
    }
    else {
        return mutation
    }
}

/**
 * A class to represent the XML {@code mutation} node.
 */
@EqualsAndHashCode(excludes = ["lineNumber", "unstable"])
@Immutable
class Mutation implements Comparable<Mutation> {

    /**
     * Mutation nodes present in suppressions file do not have a {@code lineNumber}.
     * The {@code lineNumber} is set to {@code -1} for such mutations.
     */
    private static final int LINE_NUMBER_NOT_PRESENT_VALUE = -1

    String sourceFile
    String mutatedClass
    String mutatedMethod
    String mutator
    String description
    String lineContent
    int lineNumber
    boolean unstable

    @Override
    String toString() {
        String toString = """
            Source File: "${getSourceFile()}"
            Class: "${getMutatedClass()}"
            Method: "${getMutatedMethod()}"
            Line Contents: "${getLineContent()}"
            Mutator: "${getMutator()}"
            Description: "${getDescription()}"
            """.stripIndent()
        if (getLineNumber() != LINE_NUMBER_NOT_PRESENT_VALUE) {
            toString += 'Line Number: ' + getLineNumber()
        }
        return toString
    }

    @Override
    int compareTo(Mutation other) {
        int i = getSourceFile() <=> other.getSourceFile()
        if (i != 0) {
            return i
        }

        i = getMutatedClass() <=> other.getMutatedClass()
        if (i != 0) {
            return i
        }

        i = getMutatedMethod() <=> other.getMutatedMethod()
        if (i != 0) {
            return i
        }

        i = getLineContent() <=> other.getLineContent()
        if (i != 0) {
            return i
        }

        i = getMutator() <=> other.getMutator()
        if (i != 0) {
            return i
        }

        return getDescription() <=> other.getDescription()
    }

    /**
     * XML format of the mutation.
     *
     * @return XML format of the mutation
     */
    String toXmlString() {
        return """
            <mutation unstable="${isUnstable()}">
              <sourceFile>${getSourceFile()}</sourceFile>
              <mutatedClass>${getMutatedClass()}</mutatedClass>
              <mutatedMethod>${getMutatedMethod()}</mutatedMethod>
              <mutator>${getMutator()}</mutator>
              <description>${getDescription()}</description>
              <lineContent>${getLineContent()}</lineContent>
            </mutation>
            """.stripIndent(10)
    }

}
