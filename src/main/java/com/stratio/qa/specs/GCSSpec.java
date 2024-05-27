package com.stratio.qa.specs;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.stratio.qa.utils.ThreadProperty;
import cucumber.api.java.en.When;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class GCSSpec extends BaseGSpec {

    public GCSSpec(CommonG spec) {
        this.commonspec = spec;
    }

    private void createGCSCredentialsFile() throws Exception {
        String gcsPrivateKey = System.getProperty("BDT_GCS_PRIVATE_KEY");
        if (gcsPrivateKey != null && (gcsPrivateKey.contains("-BEGINPRIVATEKEY-") || gcsPrivateKey.contains("-ENDPRIVATEKEY-"))) {
            gcsPrivateKey = gcsPrivateKey.replace("-BEGINPRIVATEKEY-", "-BEGIN PRIVATE KEY-").replace("-ENDPRIVATEKEY-", "-END PRIVATE KEY-");
        }
        JSONObject gcsJson = new JSONObject();
        gcsJson.put("type", "service_account");
        gcsJson.put("project_id", System.getProperty("BDT_GCS_PROJECT_ID"));
        gcsJson.put("private_key_id", System.getProperty("BDT_GCS_PRIVATE_KEY_ID"));
        gcsJson.put("private_key", gcsPrivateKey);
        gcsJson.put("client_email", System.getProperty("BDT_GCS_SERVICE_ACCOUNT") + "@" + System.getProperty("BDT_GCS_PROJECT_ID") + ".iam.gserviceaccount.com");
        gcsJson.put("client_id", System.getProperty("BDT_GCS_CLIENT_ID"));
        gcsJson.put("auth_uri", "https://accounts.google.com/o/oauth2/auth");
        gcsJson.put("token_uri", "https://oauth2.googleapis.com/token");
        gcsJson.put("auth_provider_x509_cert_url", "https://www.googleapis.com/oauth2/v1/certs");
        gcsJson.put("client_x509_cert_url", "https://www.googleapis.com/robot/v1/metadata/x509/" + System.getProperty("BDT_GCS_SERVICE_ACCOUNT") + "%40" + System.getProperty("BDT_GCS_PROJECT_ID") + ".iam.gserviceaccount.com");
        writeInFile(gcsJson.toString().replace("\\\\", "\\"), "gcscredentials.json");
    }

    private Storage getStorage() throws Exception {
        createGCSCredentialsFile();
        File tempDirectory = new File(System.getProperty("user.dir") + "/target/test-classes/");
        String absolutePathFile = tempDirectory.getAbsolutePath() + "/" + "gcscredentials.json";
        Credentials credentials = GoogleCredentials.fromStream(new FileInputStream(absolutePathFile));
        return StorageOptions.newBuilder().setCredentials(credentials).setProjectId("ConnectorStorage").build().getService();
    }

    /*
     * List GCS buckets and save it in environment variable
     * @param envVar : variable name for save list GCS buckets
     * @throws Exception
     */
    @When("^I list GCS buckets and save it in environment variable '(.+?)'$")
    public void listGCSBuckets(String envVar) throws Exception {
        Storage storage = getStorage();
        List<String> listBucket = new ArrayList<>();
        for (Bucket bucket : storage.list().iterateAll()) {
            listBucket.add(bucket.getName());
        }
        ThreadProperty.set(envVar, String.join("\n", listBucket));
        commonspec.getLogger().debug("GCS Buckets:\n {}", String.join("\n", listBucket));
        storage.close();
    }

    /*
     * List GCS blobs and save it in environment variable
     * @param gcsBucket : GCS Bucket
     * @param envVar : variable name for save list GCS blobs
     * @throws Exception
     */
    @When("^I list GCS blobs from bucket '(.+?)' and save it in environment variable '(.+?)'$")
    public void listGCSBlobs(String gcsBucket, String envVar) throws Exception {
        Storage storage = getStorage();
        List<String> listBlob = new ArrayList<>();
        Bucket bucket = storage.get(gcsBucket);
        for (Blob blob : bucket.list().iterateAll()) {
            listBlob.add(blob.getName());
        }
        ThreadProperty.set(envVar, String.join("\n", listBlob));
        commonspec.getLogger().debug("GCS Blobs:\n {}", String.join("\n", listBlob));
        storage.close();
    }

    /*
     * Delete GCS blob
     * @param blobName : blob name to delete
     * @param gcsBucket : GCS Bucket
     * @throws Exception
     */
    @When("^I delete GCS blob with name '(.+?)' in bucket '(.+?)'$")
    public void deleteBucket(String blobName, String gcsBucket) throws Exception {
        Storage storage = getStorage();
        Bucket bucket = storage.get(gcsBucket);
        boolean deleted = bucket.get(blobName).delete();
        commonspec.getLogger().info("Blob {} deleted? {}", blobName, deleted);
        storage.close();
    }

    /*
     * Delete GCS blob starting with prefix
     * @param blobPrefix : blob prefix
     * @param gcsBucket : GCS Bucket
     * @throws Exception
     */
    @When("^I clear GCS blobs starting with prefix '(.+?)' in bucket '(.+?)'$")
    public void deleteObjectsBucket(String blobPrefix, String gcsBucket) throws Exception {
        Storage storage = getStorage();
        Bucket bucket = storage.get(gcsBucket);
        for (Blob blob : bucket.list().iterateAll()) {
            String name = blob.getName();
            if (name.startsWith(blobPrefix)) {
                boolean deleted = blob.delete();
                commonspec.getLogger().info("Blob {} deleted? {}", name, deleted);
            }
        }
        storage.close();
    }
}
