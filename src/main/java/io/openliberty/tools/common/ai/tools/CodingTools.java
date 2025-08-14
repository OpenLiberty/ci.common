/**
 * (C) Copyright IBM Corporation 2025
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openliberty.tools.common.ai.tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.openliberty.tools.common.ai.util.Utils;

public class CodingTools {

    private String workingDirectory = System.getProperty("user.dir");

    private ArrayList <File> matchingFiles;

    private void fileMatch(File file, String name) {
        if (file.isFile() && file.getName().equalsIgnoreCase(name)) {
            matchingFiles.add(file);
        }
    }
  
    protected boolean confirmReadFile(File file) throws Exception {
        return Utils.readFile(file);
    }

    protected boolean confirmWriteFile(File file) throws Exception {
        return Utils.writeFile(file);
    }

    private File findFile(String name) throws Exception {
        Path dir = Paths.get(workingDirectory);
        matchingFiles = new ArrayList<>();
        Files.walk(dir).forEach(file -> fileMatch(file.toFile(), name));
        if (matchingFiles.size() == 0) {
            throw new Exception("Could not find the file, did the user forget the extension");
        } else if (matchingFiles.size() == 1) {
            return matchingFiles.get(0);
        } else {
            StringBuilder matches = new StringBuilder();
            for (File file : matchingFiles) {
                matches.append(file.getAbsolutePath() + '\n');
            }
            throw new Exception("There are multiple files with the name " +
                name + ". Ask the user to return the absolute path of one of the following: " +
                matches);
        }
    }

    private File getFile(String path) throws Exception{
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = findFile(path);
        }
        if (file.exists()) {
            if (!confirmReadFile(file)) {
                throw new Exception("User denied permission to the requested path");
            }
        }
        return file;
    }

    @Tool("Return current working directory")
    public String getWorkingDirectory() {
        return workingDirectory;
    }

    @Tool("Save a Java file given the package name, class name, and source code")
    public void saveFile(@P("Package name, ask user if unsure (example: com.example.project)") String packageName,
                         @P("Class name (example: Thing)") String className,
                         @P("Source code") String data) throws Exception {
        
        String pathName = "src/main/java/".replace("/", File.separator)
                          + packageName.replace(".", File.separator) + File.separator + className + ".java";
        File file = new File(pathName);
        if (!file.exists()) {
            if (confirmWriteFile(file)) {
                Path path = Paths.get(file.getAbsolutePath());
                Files.createDirectories(path.getParent());
                Files.write(path, data.getBytes(StandardCharsets.UTF_8));
            } else {
                throw new Exception("AI was not allowed to save the " + file.getAbsolutePath() + "file.");
            }
        } else {
            throw new Exception("File already exists");
        }
    }

    @Tool("Read a file/class")
    public String readFile(@P("Name of the file/class (example: HelloWorld.java)") String fileName) throws Exception {
        File file = getFile(fileName);
        if (!file.exists()) {
            throw new Exception("Could not find the file: " + file.getAbsolutePath());
        }
        return Files.readString(Paths.get(file.getAbsolutePath()));
    }

    @Tool("Read a method in a java class")
    public String readMethod(@P("Name of the java class file (example: HelloWorld.java)") String fileName,
                             @P("Method name (example: Main)") String method) throws Exception {

        File file = getFile(fileName);
        if (!file.exists()) {
            throw new Exception("Could not find the file: " + file.getAbsolutePath());
        }
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(file);
        } catch (ParseProblemException e) {
            // Cannot parse a java file that is not syntactically correct
            return readFile(fileName);
        }

        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class)
                                            .stream()
                                            .filter(m -> m.getNameAsString().equalsIgnoreCase(method))
                                            .toList();

        if (!methods.isEmpty()) {
            StringBuilder methodData = new StringBuilder();
            for (MethodDeclaration currentMethod : methods) {
                methodData.append(currentMethod.getDeclarationAsString());
                Optional<BlockStmt> bodyOptional = currentMethod.getBody();
                if (bodyOptional.isPresent()) {
                    BlockStmt body = bodyOptional.get();
                    methodData.append(" ");
                    methodData.append(body.toString());
                }
                methodData.append("\n");
            }
            return methodData.toString();
        } else {
            throw new Exception("Method '" + method + "' not found.");
        }
    }

}
