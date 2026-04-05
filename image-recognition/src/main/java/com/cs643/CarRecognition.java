package com.cs643;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.Label;
import software.amazon.awssdk.services.rekognition.model.S3Object;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class CarRecognition {

	public static String queueNew(SqsClient sqs, String queueName){
	try{return sqs.getQueueUrl(builder->builder.queueName(queueName)).queueUrl();
	}
	catch(Exception e){
	return sqs.createQueue(builder->builder.queueName(queueName)).queueUrl();
	}
	}
    public static void main(String[] args) {

        String bucketName = "cs643-njit-project1";
        String queueName = "transferQueue";
        float minConfidence = 80.0f;

        Region region = Region.US_EAST_1;

        S3Client s3 = S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        RekognitionClient rekognition = RekognitionClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        SqsClient sqs = SqsClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

	String queueUrl = queueNew(sqs, queueName);
	System.out.println("Queue initialized");
	System.out.println("Queue URL: " + queueUrl);



        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();

            ListObjectsV2Response listResponse = s3.listObjectsV2(listRequest);

            for (software.amazon.awssdk.services.s3.model.S3Object obj : listResponse.contents()) {
                String key = obj.key();

               

                System.out.println("Checking image: " + key);

                Image image = Image.builder()
                        .s3Object(S3Object.builder()
                                .bucket(bucketName)
                                .name(key)
                                .build())
                        .build();

                DetectLabelsRequest request = DetectLabelsRequest.builder()
                        .image(image)
                        .maxLabels(10)
                        .minConfidence(minConfidence)
                        .build();

                DetectLabelsResponse response = rekognition.detectLabels(request);

                boolean hasCar = false;

                for (Label label : response.labels()) {
                    if (label.name().equalsIgnoreCase("Car") && label.confidence() >= minConfidence) {
                        SendMessageRequest msgRequest = SendMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .messageBody(key)
                            .build();

                    	sqs.sendMessage(msgRequest);
		    	System.out.println("Car found in " + key+"Label: "+label.name()+" Confidence: "+label.confidence());
                        break;
                    }
                }
	    }

            SendMessageRequest doneMessage = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody("-1")
                    .build();

            sqs.sendMessage(doneMessage);

            System.out.println("Completed image scanning");

	} catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        s3.close();
        rekognition.close();
        sqs.close();
    }
}
