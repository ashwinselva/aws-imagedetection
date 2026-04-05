package com.cs643;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectTextRequest;
import software.amazon.awssdk.services.rekognition.model.DetectTextResponse;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.S3Object;
import software.amazon.awssdk.services.rekognition.model.TextDetection;
import software.amazon.awssdk.services.rekognition.model.TextTypes;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class TextRecognition {

    public static String queueNew(SqsClient sqs, String queueName){
        try{
            return sqs.getQueueUrl(builder -> builder.queueName(queueName)).queueUrl();
            
        }
        catch(Exception e){
            System.out.println("Queue not found, creating transfer queue");
            return sqs.createQueue(builder -> builder.queueName(queueName)).queueUrl();
        }
    }

    public static void main(String[] args) {

        String bucketName = "cs643-njit-project1";
        String queueName = "transferQueue";
        float minConfidence = 80.0f;

        Region region = Region.US_EAST_1;

        RekognitionClient rekognition = RekognitionClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        SqsClient sqs = SqsClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        String queueUrl = queueNew(sqs, queueName);
        

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("output.txt"));
            writer.write("Output File\n\n");

            while (true) {

                ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(1)
                        .waitTimeSeconds(10)
                        .build();

                List<Message> messages = sqs.receiveMessage(receiveRequest).messages();

                if (messages.isEmpty()) {
                    System.out.println("Waiting for image");
                    continue;
                }

                Message msg = messages.get(0);
                String imageKey = msg.body().trim();

                System.out.println("Received: " + imageKey);

                if (imageKey.equals("-1")) {
                    DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(msg.receiptHandle())
                            .build();

                    sqs.deleteMessage(deleteRequest);
                    System.out.println("Finished reading all messages");
                    break;
                }


                try {
                    Image image = Image.builder()
                            .s3Object(S3Object.builder()
                                    .bucket(bucketName)
                                    .name(imageKey)
                                    .build())
                            .build();

                    DetectTextRequest request = DetectTextRequest.builder()
                            .image(image)
                            .build();

                    DetectTextResponse response = rekognition.detectText(request);

                    String foundText = "";

                    for (TextDetection text : response.textDetections()) {
                        if (text.type() == TextTypes.LINE && text.confidence() >= minConfidence) {
                            if (!foundText.equals("")) {
                                foundText += " | ";
                            }
                            foundText += text.detectedText().trim();
                        }
                    }

                    if (!foundText.equals("")) {
                        System.out.println("Text found in " + imageKey + " Text: " + foundText);
                        writer.write("Image: " + imageKey + "\n");
                        writer.write("Text: " + foundText + "\n\n");
                    } else {
                        System.out.println("No text in " + imageKey);
                    }

                } catch (Exception e) {
                    System.out.println("Error processing image: " + imageKey);

                }

                DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(msg.receiptHandle())
                        .build();

                sqs.deleteMessage(deleteRequest);
            }

           
            writer.close();

            System.out.println("Output saved to output.txt");

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        rekognition.close();
        sqs.close();
    }
}

