/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.qa.specs;

import com.csvreader.CsvReader;
import com.stratio.qa.utils.ThreadProperty;
import io.cucumber.java.en.When;
import io.cucumber.datatable.DataTable;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generic File handling Specs.
 *
 * @see <a href="FileSpec-annotations.html">File Steps &amp; Matching Regex</a>
 */
public class FileSpec extends BaseGSpec {

    /**
     * Generic constructor.
     *
     * @param spec object
     */
    public FileSpec(CommonG spec) {
        this.commonspec = spec;

    }

    /**
     * Read csv file and store result in list of maps
     *
     * @param csvFile
     */
    @When("^I read info from csv file '(.+?)' with separator '(.+?)'$")
    public void readFromCSV(String csvFile, String separator) throws Exception {
        //By default separator is a coma
        char sep = ',';
        if (separator.length() > 1) {
            switch (separator) {
                case "\\t":
                    sep = '\t';
                    break;
                default:
                    sep = ',';
                    break;
            }
        } else {
            sep = separator.charAt(0);
        }

        CsvReader rows = new CsvReader(csvFile, sep);

        String[] columns = null;
        if (rows.readRecord()) {
            columns = rows.getValues();
            rows.setHeaders(columns);
        }

        List<Map<String, String>> results = new ArrayList<Map<String, String>>();
        while (rows.readRecord()) {
            Map<String, String> row = new HashMap<String, String>();
            for (String column : columns) {
                row.put(column, rows.get(rows.getIndex(column)));
            }
            results.add(row);
        }

        rows.close();

        commonspec.setResultsType("csv");
        commonspec.setCSVResults(results);
    }

    /**
     * Create a JSON in resources directory with given name, so for using it you've to reference it as:
     * $(pwd)/target/test-classes/fileName
     *
     * @param fileName      name of the JSON file to be created
     * @param baseData      path to file containing the schema to be used
     * @param type          element to read from file (element should contain a json)
     * @param modifications DataTable containing the modifications to be done to the base schema element
     *                      <p>
     *                      - Syntax will be:
     *                      {@code
     *                      | <key path> | <type of modification> | <new value> |
     *                      }
     *                      for DELETE/ADD/UPDATE/APPEND/PREPEND
     *                      where:
     *                      key path: path to the key to be modified
     *                      type of modification: DELETE/ADD/UPDATE/APPEND/PREPEND
     *                      new value: new value to be used
     *                      <p>
     *                      - Or:
     *                      {@code
     *                      | <key path> | <type of modification> | <new value> | <new value type> |
     *                      }
     *                      for REPLACE
     *                      where:
     *                      key path: path to the key to be modified
     *                      type of modification: REPLACE
     *                      new value: new value to be used
     *                      json value type: type of the json property (array|object|number|boolean|null|n/a (for string))
     *                      <p>
     *                      <p>
     *                      For example:
     *                      <p>
     *                      (1)
     *                      If the element read is {"key1": "value1", "key2": {"key3": "value3"}}
     *                      and we want to modify the value in "key3" with "new value3"
     *                      the modification will be:
     *                      | key2.key3 | UPDATE | "new value3" |
     *                      being the result of the modification: {"key1": "value1", "key2": {"key3": "new value3"}}
     *                      <p>
     *                      (2)
     *                      If the element read is {"key1": "value1", "key2": {"key3": "value3"}}
     *                      and we want to replace the value in "key2" with {"key4": "value4"}
     *                      the modification will be:
     *                      | key2 | REPLACE | {"key4": "value4"} | object |
     *                      being the result of the modification: {"key1": "value1", "key2": {"key4": "value4"}}
     * @throws Exception
     */
    @When("^I create file '(.+?)' based on '(.+?)' as '(.+?)' with:$")
    public void createFile(String fileName, String baseData, String type, DataTable modifications) throws Exception {
        // Retrieve data
        String retrievedData = commonspec.retrieveData(baseData, type);

        // Modify data
        commonspec.getLogger().debug("Modifying data {} as {}", retrievedData, type);
        String modifiedData = commonspec.modifyData(retrievedData, type, modifications).toString();

        // Store the file
        writeInFile(modifiedData, fileName, false);
    }

    /**
     * Read the file passed as parameter, perform the modifications specified and save the result in the environment
     * variable passed as parameter.
     *
     * @param baseData      file to read
     * @param type          whether the info in the file is a 'json' or a simple 'string'
     * @param envVar        name of the variable where to store the result
     * @param modifications modifications to perform in the content of the file
     */
    @When("^I read file '(.+?)' as '(.+?)' and save it in environment variable '(.+?)' with:$")
    public void readFileToVariable(String baseData, String type, String envVar, DataTable modifications) throws Exception {
        // Retrieve data
        String retrievedData = commonspec.retrieveData(baseData, type);

        // Modify data
        commonspec.getLogger().debug("Modifying data {} as {}", retrievedData, type);
        String modifiedData = commonspec.modifyData(retrievedData, type, modifications).toString();

        // Save in environment variable
        ThreadProperty.set(envVar, modifiedData);
    }

    /**
     * Read the file passed as parameter and save the result in the environment
     * variable passed as parameter.
     *
     * @param baseData file to read
     * @param type     whether the info in the file is a 'json' or a simple 'string'
     * @param envVar   name of the variable where to store the result
     */
    @When("^I read file '(.+?)' as '(.+?)' and save it in environment variable '(.+?)'$")
    public void readFileToVariableNoDataTable(String baseData, String type, String envVar) throws Exception {
        // Retrieve data
        String retrievedData = commonspec.retrieveData(baseData, type);

        // Save in environment variable
        ThreadProperty.set(envVar, retrievedData);
    }

    /**
     * Method to convert one json to yaml file - backup&restore functionality
     * <p>
     * File will be placed on path /target/test-classes
     */
    @When("^I convert the json file '(.+?)' to yaml file '(.+?)'$")
    public void convertJsonToYaml(String fileToConvert, String fileName) throws Exception {

        // Retrieve data
        String retrievedData = commonspec.asYaml(fileToConvert);

        // Store the file
        writeInFile(retrievedData, fileName, true);
    }

    @When("^I convert the yaml file '(.+?)' to json file '(.+?)'$")
    public void convertYamlToJson(String fileToConvert, String fileName) throws Exception {

        // Retrieve data
        String retrievedData = commonspec.convertYamlToJson(fileToConvert);

        // Store the file
        writeInFile(retrievedData, fileName, true);
    }

    /**
     * Converts a XML file to JSON
     */
    @When("^I convert the xml file '(.+?)' to json file '(.+?)'$")
    public void convertXmlToJson(String fileToConvert, String fileName) throws Exception {

        // Retrieve data
        String retrievedData = commonspec.convertXmlToJson(fileToConvert);

        // Store the file
        writeInFile(retrievedData, fileName, true);
    }

    @When("^I create zip file '(.+?)' from the following files:$")
    public void createZip(String zipPath, DataTable table) throws Exception {

        List<String> pathList = table.asList(String.class);

        try {
            byte[] buffer = new byte[1024];
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath))) {
                for (int i = 0; i < pathList.size(); i++) {
                    File srcFile = new File(pathList.get(i));
                    try (FileInputStream fis = new FileInputStream(srcFile)) {
                        zos.putNextEntry(new ZipEntry(srcFile.getName()));
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, length);
                        }
                        zos.closeEntry();
                    }
                }
            }
        } catch (IOException ioe) {
            throw new Exception("Error creating zip file: " + ioe);
        }
    }

    /**
     * Step to write a text to a file, using the doc string as the file contents
     * File will be placed on path /target/test-classes
     * Usage:
     * Given I create text file 'test_file.txt' using the following text:
     *     """
     *     line 1
     *     line 2
     *
     *     line 3
     *     """
     */
    @When("^I create text file '(.+?)' using the following text:$")
    public void createFileFromText(String fileName, String fileContents) throws Exception {
        // Store the file
        writeInFile(fileContents, fileName, true);
        commonspec.getLogger().debug("./target/test-classes" + fileName + " created with content: \n\n" + fileContents + "\n");
    }

    /**
     * Save the content of a variable to a file.
     * File will be placed on path /target/test-classes
     *
     * @param varName  the environment variable whose content is to be saved
     * @param fileName the file where the content will be saved
     */
    @When("^I save the content of variable '(.+?)' to file '(.+?)'$")
    public void saveVariableToFile(String varName, String fileName) throws Exception {
        String content = System.getProperty(varName, ThreadProperty.get(varName));
        if (content == null) {
            throw new IllegalArgumentException("Environment variable " + varName + " is not set.");
        }
        writeInFile(content, fileName, true);
        commonspec.getLogger().debug("./target/test-classes" + fileName + " created with content: \n\n" + content + "\n");
    }

    /**
     * Append a new line(s) to a CSV file at target/test-classes/<fileName> from a DataTable.
     *
     * @param fileName  name of the CSV file to append to
     * @param delimiter delimiter to use in the CSV file (ex.: ',', ';', '\t')
     * @param dataTable DataTable containing the data to append (each row will be a new line)
     */
    @When("^I add rows to the CSV file '(.+?)' with delimiter '(.+?)' and data:$")
    public void appendToCSV(String fileName, String delimiter, DataTable dataTable) throws Exception {
        StringBuilder csvLine = new StringBuilder();
        List<List<String>> rows = dataTable.asLists(String.class);

        for (List<String> row : rows) {
            csvLine.append(String.join(delimiter, row)).append("\n");
        }

        appendToFile(csvLine.toString().trim(), fileName, true);
    }

}

