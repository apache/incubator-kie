/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.drools.example.api.defaultkiesessionfromfile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.KieRepository;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultKieSessionFromByteArrayExampleTest {

    private static final String NL = System.getProperty("line.separator");

    @Test
    public void testGo() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        go(ps);
        ps.close();

        String actual = baos.toString();
        String expected = "" +
                          "Dave: Hello, HAL. Do you read me, HAL?" + NL +
                          "HAL: Dave. I read you." + NL;
        assertThat(actual).isEqualTo(expected);
    }
    
    public void go(PrintStream out) {
        KieServices ks = KieServices.Factory.get();
        KieRepository kr = ks.getRepository();

        KieModule kModule = kr.addKieModule(ks.getResources().newByteArrayResource(getKjarAsByteArray(getFile("default-kiesession"))));

        KieContainer kContainer = ks.newKieContainer(kModule.getReleaseId());

        KieSession kSession = kContainer.newKieSession();
        kSession.setGlobal("out", out);

        Object msg1 = createMessage(kContainer, "Dave", "Hello, HAL. Do you read me, HAL?");
        kSession.insert(msg1);
        kSession.fireAllRules();
    }

    private static Object createMessage(KieContainer kContainer, String name, String text) {
        Object o = null;
        try {
            Class cl = kContainer.getClassLoader().loadClass("org.drools.example.api.defaultkiesession.Message");
            o = cl.getConstructor(new Class[]{String.class, String.class}).newInstance(name, text);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return o;
    }
    
    public byte[] getKjarAsByteArray(File file) {
		try {
			InputStream is = new FileInputStream(file);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            int nRead;
            byte[] data = new byte[16384];

            while ((nRead = is.read(data, 0, data.length)) != -1) {
              buffer.write(data, 0, nRead);
            }

            buffer.flush();

            return buffer.toByteArray();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
    }

    public static File getFile(String exampleName) {
        File folder = new File("drools-examples-api").getAbsoluteFile();
        File exampleFolder = null;
        while (folder != null) {
            exampleFolder = new File(folder, exampleName);
            if (exampleFolder.exists()) {
                break;
            }
            exampleFolder = null;
            folder = folder.getParentFile();
        }

        if (exampleFolder != null) {

            File targetFolder = new File(exampleFolder, "target");
            if (!targetFolder.exists()) {
                throw new RuntimeException("The target folder does not exist, please build project " + exampleName + " first");
            }

            FilenameFilter expectedJArFilter = (d, str ) -> str.startsWith(exampleName) &&
                    str.endsWith(".jar") &&
                    !str.endsWith("-sources.jar") &&
                    !str.endsWith("-tests.jar") &&
                    !str.endsWith("-javadoc.jar");
            String[] foundFile = targetFolder.list(expectedJArFilter);
            if (foundFile == null || foundFile.length == 0) {
                throw new RuntimeException("The target jar does not exist, please build project " + exampleName + " first");
            } else if (foundFile.length > 1) {
                String errorFiles =  Arrays.toString(foundFile);
                throw new RuntimeException("Multiple matching files exists: " + errorFiles + "; please check!");
            }
            return new File(targetFolder, foundFile[0]);
        }

        throw new RuntimeException("The target jar does not exist, please build project " + exampleName + " first");
    }

}
