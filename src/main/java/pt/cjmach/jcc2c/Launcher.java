/*
 *  Copyright 2024 Carlos Machado
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package pt.cjmach.jcc2c;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import picocli.CommandLine;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

/**
 *
 * @author cmachado
 */
@Command(name = "jcc2c",
        description = "A tool for converting JaCoCo XML coverage reports into Cobertura XML coverage reports.")
public class Launcher implements Callable<Integer> {
    @Option(names = {"-i", "--input"}, paramLabel = "FILE", required = true,
            description = "Path to JaCoCo XML coverage report input file.")
    private File inputFile;
    
    @Option(names = {"-o", "--output"}, paramLabel = "FILE", required = true,
            description = "Path to Cobertura XML coverage report output file.")
    private File outputFile;
    
    @Parameters(paramLabel = "SOURCE DIR", description = "One or more source directories.")
    private File[] sourceRoots;
    
    /**
     * 
     */
    @Option(names = {"-v", "--version"}, versionHelp = true, description = "Print version and exit.")
    @SuppressWarnings("FieldMayBeFinal")
    private boolean versionRequested = false;
    
    /**
     * 
     */
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Print help and exit.")
    @SuppressWarnings("FieldMayBeFinal")
    private boolean helpRequested = false;
    
    @Override
    public Integer call() throws Exception {
        ReportConverter converter = new ReportConverter();
        if (sourceRoots == null || sourceRoots.length == 0) {
            sourceRoots = new File[] { new File(".") };
        }
        converter.convert(inputFile, outputFile, sourceRoots);
        return 0;
    }

    public static void main(String[] args) {
        CommandLine cmdLine = new CommandLine(new Launcher());
        cmdLine.setCaseInsensitiveEnumValuesAllowed(true);
        int exitCode = cmdLine.execute(args);
        if (cmdLine.isVersionHelpRequested()) {
            String version = getVersion();
            System.out.println("jcc2c " + version); // NOI18N
        }
        System.exit(exitCode);
    }
    
    /**
     * 
     * @return 
     */
    private static String getVersion() {
        try {
            Manifest manifest = new Manifest(Launcher.class.getResourceAsStream("/META-INF/MANIFEST.MF")); // NOI18N
            Attributes attributes = manifest.getMainAttributes();
            String version = attributes.getValue("Implementation-Version"); // NOI18N
            return version;
        } catch (IOException ex) {
            System.err.println("Could not read MANIFEST.MF file");
            return "";
        }
    }
}
