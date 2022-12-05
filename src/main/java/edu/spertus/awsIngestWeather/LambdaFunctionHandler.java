package edu.spertus.awsIngestWeather;

import java.io.File;
import java.io.InputStream;

import com.amazonaws.services.s3.event.S3EventNotification;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

public class LambdaFunctionHandler implements RequestHandler<S3Event, String> {

    private AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();

    public LambdaFunctionHandler() {}

    // Test purpose only.
    LambdaFunctionHandler(AmazonS3 s3) {
        this.s3 = s3;
    }

    @Override
    public String handleRequest(S3Event event, Context context) {
        context.getLogger().log("Received event: " + event);
        Transform transformer = new Transform(context);

        S3EventNotification.S3EventNotificationRecord record = event.getRecords().get(0);
        String bucket = record.getS3().getBucket().getName();
        // Object key may have spaces or unicode non-ASCII characters.
        String key = record.getS3().getObject().getUrlDecodedKey();

        try {
        	// Adapted from https://docs.aws.amazon.com/lambda/latest/dg/with-s3-example-deployment-pkg.html
            S3Object response = s3.getObject(new GetObjectRequest(bucket, key));
            String contentType = response.getObjectMetadata().getContentType();

            // Download the weather tar from S3 into a stream
            S3Object s3Object = s3.getObject(new GetObjectRequest(bucket, key));
            InputStream objectData = s3Object.getObjectContent();

            // transform the tar to avro
            String transFileName = key.substring(0, key.length() - 4) + ".avro";
            File transformed = new File("/tmp/" + transFileName);
            transformer.transform(new TarArchiveInputStream(objectData), transformed);

            // Upload to S3
            PutObjectRequest request = new PutObjectRequest("mpcs53014-2022-work", transFileName, transformed);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("binary/octet-stream");
            request.setMetadata(metadata);
            s3.putObject(request); 

            return contentType;
        } catch (Exception e) {
            e.printStackTrace();
            context.getLogger().log(String.format(
                "Error getting object %s from bucket %s. Make sure they exist and"
                + " your bucket is in the same region as this function.", key, bucket));
            throw e;
        }
    }
}