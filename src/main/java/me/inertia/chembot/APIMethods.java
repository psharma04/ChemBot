package me.inertia.chembot;

import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.auth.AWSCredentials;
import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder;
import com.ibm.cloud.objectstorage.oauth.BasicIBMOAuthCredentials;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;
import com.ibm.cloud.objectstorage.services.s3.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static me.inertia.chembot.Main.*;

public class APIMethods {

    public static String getDiscordAPI(boolean test){
        try {
            if(test) {
                BufferedReader br = new BufferedReader(new FileReader(new File("data/KEYS/discordtest.api")));
                //String s = br.readLine();
                //System.out.println(s);
                return br.readLine();
            }else{
                BufferedReader br = new BufferedReader(new FileReader(new File("data/KEYS/discord.api")));
                return br.readLine();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Object getObjectFromAPI(String bucketName,String objectKey){
        GetObjectRequest request = new // create a new request to get an object
                GetObjectRequest( // request the new object by identifying
                bucketName, // the name of the bucket
                objectKey // the name of the object
        );

        _cos.getObject( // write the contents of the object
                request, // using the request that was just created
                new File("temp.txt") // to write to a new file
        );
        try {
            FileInputStream fileIn = new FileInputStream(new File("temp.txt"));
            ObjectInputStream objIn = new ObjectInputStream(fileIn);
            Object tempObj = objIn.readObject();
            objIn.close();
            return tempObj;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void writeObjectToAPI(Object in, String bucketName, String objectKey){
        try {
            FileOutputStream fileOut = new FileOutputStream("temp.txt");
            ObjectOutputStream objOut = new ObjectOutputStream(fileOut);
            objOut.writeObject(in);
            fileOut.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        _cos.putObject(
                bucketName, // the name of the destination bucket
                objectKey, // the object key
                new File("temp.txt")
        );
    }

    public static ArrayList<String> getAPIObjectList(AmazonS3 cosClient, String bucketName)
    {
        ObjectListing objectListing = cosClient.listObjects(new ListObjectsRequest().withBucketName(bucketName));
        ArrayList<String> objects = new ArrayList<>();
        for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
            objects.add(objectSummary.getKey());
        }
        return objects;
    }


    public static void uploadFromFile(String bucketName, String objectKey, File destination){
        _cos.putObject(
                bucketName, // the name of the destination bucket
                objectKey, // the object key
                destination // the file name and path of the object to be uploaded e.g. "/home/user/test.txt"
        );
    }

    public static void downloadToFile(String bucketName,String objectKey,File downloadPath){
                GetObjectRequest request = new // create a new request to get an object
                GetObjectRequest( // request the new object by identifying
                bucketName, // the name of the bucket
                objectKey // the name of the object
        );

        _cos.getObject( // write the contents of the object
                request, // using the request that was just created
                downloadPath // to write to a new file
        );
    }

    public static String getCloudAPI(){
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File("data/KEYS/cloud.api")));
            return br.readLine();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    public static void createTextFile(String bucketName, String itemName, String fileText) {
        System.out.printf("Creating new item: %s\n", itemName);

        InputStream newStream = new ByteArrayInputStream(fileText.getBytes(StandardCharsets.UTF_8));

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(fileText.length());

        PutObjectRequest req = new PutObjectRequest(bucketName, itemName, newStream, metadata);
        _cos.putObject(req);

        System.out.printf("Item: %s created!\n", itemName);
    }

    public static boolean testForObject(String key){
        return getAPIObjectList(_cos, bucketName).contains(key);
    }

    public static AmazonS3 createClient(String api_key, String service_instance_id, String endpoint_url, String location)
    {
        AWSCredentials credentials = new BasicIBMOAuthCredentials(api_key, service_instance_id);
        ClientConfiguration clientConfig = new ClientConfiguration().withRequestTimeout(5000);
        clientConfig.setUseTcpKeepAlive(true);

        AmazonS3 cos = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint_url, location)).withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfig).build();

        return cos;
    }
}

