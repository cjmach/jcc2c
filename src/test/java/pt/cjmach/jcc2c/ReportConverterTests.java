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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author cmachado
 */
public class ReportConverterTests {
    
    @Test
    public void testInput1JaCoCoReportConversionSucceeds() {
        File inputFile = new File("./src/test/resources/pt/cjmach/jcc2c/input1.xml");
        File expectedFile = new File("./src/test/resources/pt/cjmach/jcc2c/output1.xml");
        validateReportConversion(inputFile, expectedFile);
    }
    
    @Test
    public void testInput2JaCoCoReportConversionSucceeds() {
        File inputFile = new File("./src/test/resources/pt/cjmach/jcc2c/input2.xml");
        File expectedFile = new File("./src/test/resources/pt/cjmach/jcc2c/output2.xml");
        validateReportConversion(inputFile, expectedFile);
    }
    
    private void validateReportConversion(File inputFile, File expectedFile) {
        File outputFile = new File("-");
        File[] sourceDirs = new File[] { new File("./src/main/java") };
        PrintStream oldOut = System.out;
        String output = null;
        try {
            ReportConverter converter = new ReportConverter();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));            
            converter.convert(inputFile, outputFile, sourceDirs);
            output = baos.toString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            fail(ex);
        } finally {
            System.setOut(oldOut);
        }
        
        assertTrue(output != null);
        
        try {
            String expected = Files.readString(expectedFile.toPath(), StandardCharsets.UTF_8);
            assertEquals(output, expected);
        } catch (IOException ex) {
            fail(ex);
        }
    }
}
