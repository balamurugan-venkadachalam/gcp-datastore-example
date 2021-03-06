package com.example.demo;

import com.google.datastore.v1.*;
import com.google.datastore.v1.client.Datastore;
import com.google.datastore.v1.client.DatastoreException;
import com.google.datastore.v1.client.DatastoreFactory;
import com.google.datastore.v1.client.DatastoreHelper;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * A trivial command-line application using the Datastore API.
 */
public class Adams {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Adams <PROJECT_ID>");
            System.exit(1);
        }
        // Set the project ID from the command line parameters.
        String projectId = args[0];
        Datastore datastore = null;
        try {
            // Setup the connection to Google Cloud Datastore and infer credentials
            // from the environment.
            datastore = DatastoreFactory.get().create(DatastoreHelper.getOptionsFromEnv()
                    .projectId(projectId).build());
        } catch (GeneralSecurityException exception) {
            System.err.println("Security error connecting to the datastore: " + exception.getMessage());
            System.exit(1);
        } catch (IOException exception) {
            System.err.println("I/O error connecting to the datastore: " + exception.getMessage());
            System.exit(1);
        }

        try {
            // Create an RPC request to begin a new transaction.
            BeginTransactionRequest.Builder treq = BeginTransactionRequest.newBuilder();
            // Execute the RPC synchronously.
            BeginTransactionResponse tres = datastore.beginTransaction(treq.build());
            // Get the transaction handle from the response.
            ByteString tx = tres.getTransaction();

            // Create an RPC request to get entities by key.
            LookupRequest.Builder lreq = LookupRequest.newBuilder();
            // Set the entity key with only one `path`: no parent.
            Key.Builder key = Key.newBuilder().addPath(
                    Key.PathElement.newBuilder()
                            .setKind("Trivia")
                            .setName("hgtg"));
            // Add one key to the lookup request.
            lreq.addKeys(key);
            // Set the transaction, so we get a consistent snapshot of the
            // entity at the time the transaction started.
            lreq.getReadOptionsBuilder().setTransaction(tx);
            // Execute the RPC and get the response.
            LookupResponse lresp = datastore.lookup(lreq.build());
            // Create an RPC request to commit the transaction.
            CommitRequest.Builder creq = CommitRequest.newBuilder();
            // Set the transaction to commit.
            creq.setTransaction(tx);
            Entity entity;
            if (lresp.getFoundCount() > 0) {
                entity = lresp.getFound(0).getEntity();
            } else {
                // If no entity was found, create a new one.
                Entity.Builder entityBuilder = Entity.newBuilder();
                // Set the entity key.
                entityBuilder.setKey(key);
                // Add two entity properties:
                // - a UTF-8 string: `question`
                entityBuilder.putProperties(
                        "question",
                        Value.newBuilder().setStringValue("Meaning of Life?").build());
                // - a 64bit integer: `answer`
                entityBuilder.putProperties(
                        "answer",
                        Value.newBuilder().setIntegerValue(42).build());
                // Build the entity.
                entity = entityBuilder.build();
                // Insert the entity in the commit request mutation.
                creq.addMutationsBuilder().setInsert(entity);
            }
            // Execute the Commit RPC synchronously and ignore the response.
            // Apply the insert mutation if the entity was not found and close
            // the transaction.
            datastore.commit(creq.build());
            // Get `question` property value.
            String question = entity.getPropertiesMap().get("question").getStringValue();
            // Get `answer` property value.
            Long answer = entity.getPropertiesMap().get("answer").getIntegerValue();
            System.out.println(question);
            String result = System.console().readLine("> ");
            if (result.equals(answer.toString())) {
                System.out.println("fascinating, extraordinary and, " +
                        "when you think hard about it, completely obvious.");
            } else {
                System.out.println("Don't Panic!");
            }
        } catch (DatastoreException exception) {
            // Catch all Datastore rpc errors.
            System.err.println("Error while doing datastore operation");
            // Log the exception, the name of the method called and the error code.
            System.err.println(String.format("DatastoreException(%s): %s %s",
                    exception.getMessage(),
                    exception.getMethodName(),
                    exception.getCode()));
            System.exit(1);
        }
    }
}