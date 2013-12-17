package net.twasink.s3object;

import static org.hamcrest.Matchers.array;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsEqual.equalTo;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.collection.IsArray;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

/**
 * An example end-to-end test that ensure that the S3 Object Streamer works as advertised. This test first uploads a
 * medium-size object (4MB of generated data) to S3, and then downloads it. The resulting data is then processed in 
 * blocks of 512MB, with a 1 minute enforced wait in between.
 * 
 * In order to run this test, you will need to specify a number of environment properties. These are:
 * * AWS_ACCESS_KEY_ID
 * * AWS_SECRET_KEY
 * * AWS_REGION - defaults to 'us-east-1'
 * * S3_BUCKET
 * 
 * Alternatively, these can be provided as system properties:
 * * aws.accessKeyId
 * * aws.secretKey
 * * aws.region - defaults to 'us-east-1'
 * * s3bucket
 * 
 * Your AWS account will obviously need to be able to write to and read from the nominated bucket.
 * 
 * A random file name will be used (using a UUID); the test _will_ try to delete the file afterwards, of course.
 */
public class EndToEndTest {
    private static final Logger LOG = Logger.getLogger("EndToEndTest");
    
    private String region;
    private String bucketName;
    private String objectId;
    
    @Before
    public void determineRegion() {
        this.region = System.getenv("AWS_REGION");
        if (this.region == null || this.region.length() == 0) {
            this.region = System.getProperty("aws.region", "us-east-1");
        }
    }
    
    @Before
    public void determineBucketName() {
        this.bucketName = System.getenv("S3_BUCKET");
        if (this.bucketName == null || this.bucketName.length() == 0) {
            this.bucketName = System.getProperty("s3Bucket");
        }
        Assume.assumeThat("You need to specify which S3 Bucket to use", this.bucketName, not(isEmptyOrNullString()));
    }
    
    @After
    public void cleanupTestData() {
        AmazonS3Client s3Client = new AmazonS3Client();
        s3Client.setRegion(RegionUtils.getRegion(this.region));

        LOG.info("Deleting file " + this.objectId + " from " + this.bucketName);
        s3Client.deleteObject(this.bucketName, this.objectId);
        LOG.info("File " + this.objectId + " deleted from " + this.bucketName);
    }
    
    @Test
    public void test() throws IOException {
        AmazonS3Client s3Client = new AmazonS3Client(new ClientConfiguration().withSocketTimeout(10000));
        s3Client.setRegion(RegionUtils.getRegion(this.region));
        verifyS3BucketExists(s3Client);
        
        byte[] data = createRandomData();

        this.objectId = UUID.randomUUID().toString();

        uploadDataToS3(data, s3Client);
        
        s3Client = new AmazonS3Client(new ClientConfiguration().withSocketTimeout(1000));
        s3Client.setRegion(RegionUtils.getRegion(this.region));
        S3Object object = s3Client.getObject(this.bucketName, this.objectId);
        
        try(S3ObjectInputStream dataStream = object.getObjectContent()) {
            int bytesToRead = 512 * 1024;
            for (int i = 0; i < 4; i++) {
                byte[] bufferedData = new byte[bytesToRead];
                
                LOG.info("Reading block " + i + " of object " + this.objectId + " from " + this.bucketName);
                
                int bytesRead = 0;
                while (bytesRead < bytesToRead) {
                    bytesRead += dataStream.read(bufferedData, bytesRead, bytesToRead - bytesRead);
                }
                
                LOG.info("Read block " + i + " of object " + this.objectId + " from " + this.bucketName);
                
                byte[] targetData = Arrays.copyOfRange(data, i * bytesToRead, (i + 1) * bytesToRead);
                Assert.assertArrayEquals("Data is not equal for block " + i, targetData, bufferedData);
                
                dataStream.getHttpRequest().abort(); // simulate the timeout; it turns out to be hard to reproduce it.
            }
        }
    }

    private void uploadDataToS3(byte[] data, AmazonS3Client s3Client) {
        LOG.info("Uploading file " + this.objectId + " to bucket" + this.bucketName);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentEncoding("application/octet-stream");
        metadata.setContentLength(data.length);
        
        PutObjectRequest putRequest = new PutObjectRequest(this.bucketName, objectId, new ByteArrayInputStream(data), metadata);
        s3Client.putObject(putRequest);
        LOG.info("File " + this.objectId + " uploaded to bucket" + this.bucketName);
    }

    private byte[] createRandomData() {
        byte[] data = new byte[2 * 1024 * 1024];
        new Random().nextBytes(data);
        return data;
    }

    private void verifyS3BucketExists(AmazonS3Client s3Client) {
        LOG.info("Verifying that bucket " + this.bucketName + " exists");
        Assume.assumeTrue("The S3 bucket must exist", s3Client.doesBucketExist(this.bucketName));
        LOG.info("Bucket " + this.bucketName + " does exist");
    }

}
