package com.stratio.qa.specs;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.file.datalake.*;
import com.azure.storage.file.datalake.models.ListPathsOptions;
import com.azure.storage.file.datalake.models.PathItem;
import com.stratio.qa.utils.ThreadProperty;
import io.cucumber.java.en.When;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AzureSpec extends BaseGSpec {

    public AzureSpec(CommonG spec) {
        this.commonspec = spec;
    }

    private DataLakeServiceClient getAzureDataLakeClient(String storageAccountName) {
        DataLakeServiceClient dataLakeServiceClient;
        String endpoint = "https://" + storageAccountName + ".dfs.core.windows.net";
        dataLakeServiceClient = new DataLakeServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
        return dataLakeServiceClient;
    }

    @When("^I list files( recursively)? in Azure with storage account '(.+?)', fileSystem '(.+?)' and base path '(.+?)' and save it in environment variable '(.+?)'$")
    public List<String> listFiles(String recursive, String storageAccountName, String fileSystemName, String directoryPath, String envVar) {
        DataLakeFileSystemClient fileSystemClient = getAzureDataLakeClient(storageAccountName).getFileSystemClient(fileSystemName);
        ListPathsOptions options = new ListPathsOptions()
                .setPath(directoryPath)
                .setRecursive(recursive != null);
        // List files and directories
        List<String> filesList = new ArrayList<>();
        for (PathItem pathItem : fileSystemClient.listPaths(options, Duration.ofMinutes(5))) {
            filesList.add(pathItem.getName());
        }
        ThreadProperty.set(envVar, String.join("\n", filesList));
        return filesList;
    }

    @When("^I delete path( recursively)? '(.+?)' in Azure with storage account '(.+?)' and fileSystem '(.+?)'$")
    public void deletePath(String recursive, String path, String storageAccountName, String fileSystemName) {
        // Obtener el cliente del sistema de archivos
        DataLakeFileSystemClient fileSystemClient = getAzureDataLakeClient(storageAccountName).getFileSystemClient(fileSystemName);
        // Primero, intenta obtener el cliente del directorio
        DataLakeDirectoryClient directoryClient = fileSystemClient.getDirectoryClient(path);
        boolean isDirectory = false;
        try {
            directoryClient.getProperties(); // Si no lanza una excepción, es un directorio
            isDirectory = true;
        } catch (Exception e) {
            // Si el directorio no existe, intentar con un archivo
            // Obtener el cliente del archivo
            DataLakeFileClient fileClient = fileSystemClient.getFileClient(path);
            try {
                fileClient.getProperties(); // Si no lanza una excepción, es un archivo
            } catch (Exception ex) {
                commonspec.getLogger().error("Path not found: {}", path);
                return;
            }
        }
        if (isDirectory) {
            if (recursive != null) {
                directoryClient.deleteRecursively();
            } else {
                directoryClient.delete();
            }
            commonspec.getLogger().info("Directory {} deleted {}", path, recursive != null ? "recursively" : "");
        } else {
            DataLakeFileClient fileClient = fileSystemClient.getFileClient(path);
            fileClient.delete();
            commonspec.getLogger().info("File {} deleted", path);
        }
    }

    @When("^I delete paths( recursively)? with prefix '(.+?)' in Azure with storage account '(.+?)' and fileSystem '(.+?)'$")
    public void deletePathWithPrefix(String recursive, String prefix, String storageAccountName, String fileSystemName) {
        List<String> files = listFiles(recursive, storageAccountName, fileSystemName, "/", "azureFiles");
        for (String file : files) {
            if (file.startsWith(prefix)) {
                try {
                    deletePath(recursive, file, storageAccountName, fileSystemName);
                } catch (Exception e) {
                    commonspec.getLogger().warn("Error deleting file " + file + " " + e.getMessage());
                }
            }
        }
    }
}
