package store.pocketbox.app.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import store.pocketbox.app.service.S3Component;
import store.pocketbox.app.service.S3Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class S3ServiceImpl implements S3Service {

    @Autowired
    S3Component s3;

    @Override
    public String createPreSignedForUploadFile(FilePath path) {
        if(!isFolderExists(path.getParentFolder())) {
            throw new UnsupportedOperationException("");
        }

        PutObjectRequest objectRequest = PutObjectRequest.builder().bucket(s3.bucketName).key(path.canonicalPath)
                //.contentType("text/plain")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(10)).putObjectRequest(objectRequest).build();
        PresignedPutObjectRequest presignedRequest = s3.getS3Presigner().presignPutObject(presignRequest);
        String myURL = presignedRequest.url().toString();

        return myURL;
    }

    @Override
    public String createPreSignedForDownloadFile(FilePath path) {

        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(s3.bucketName).key(path.canonicalPath).build();

        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(60)).getObjectRequest(getObjectRequest).build();

        PresignedGetObjectRequest presignedGetObjectRequest = s3.getS3Presigner().presignGetObject(getObjectPresignRequest);
        String theUrl = presignedGetObjectRequest.url().toString();

        return theUrl;
    }

    @Override
    public void deleteFile(FilePath path) {
        if(!isFolderExists(path.getParentFolder())) {
            throw new UnsupportedOperationException("");
        }

        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(s3.bucketName).key(path.canonicalPath).build();

        s3.getS3Client().deleteObject(deleteObjectRequest);
    }

    @Override
    public void createFolder(FolderPath path) {
        guaranteeFolderExists(path);
    }

    @Override
    public ListResult listFolder(FolderPath path) {
        if(!isFolderExists(path)) {
            throw new UnsupportedOperationException("");
        }

        var resp = runListRequest(path);

        var directories = listSubDirectoriesNames(resp);
        var files = listFilesNames(resp);

        return new ListResult(directories, files);
    }

    @Override
    public void deleteFolder(FolderPath path) {
        deleteFolderRecursive(path, 0);
    }

    private void deleteFolderRecursive(FolderPath path, int depth) {
        if (depth > 5) {
            return;
        }

        var resp = runListRequest(path);
        var directories = listSubDirectoriesNames(resp);
        var files = listFilesNames(resp);

        files.forEach(this::deleteFile);
        directories.forEach((x) -> deleteFolderRecursive(x, depth + 1));
    }

    private void runCreateFolderRequest(FolderPath path) {
        PutObjectRequest objectRequest = PutObjectRequest.builder().bucket(s3.bucketName).key(path + "/.folder").contentType("text/plain").build();

        s3.getS3Client().putObject(objectRequest, RequestBody.fromBytes(new byte[]{0x01}));
    }

    private ListObjectsV2Response runListRequest(FolderPath path) {
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder().bucket(s3.bucketName).prefix(path.canonicalPath).delimiter("/").build();

        ListObjectsV2Response resp = s3.getS3Client().listObjectsV2(listRequest);

        return resp;
    }

    private List<FolderPath> listSubDirectoriesNames(ListObjectsV2Response resp) {
        return resp.commonPrefixes().stream().map((x) -> new FolderPath(Arrays.stream(x.prefix().split("/")).toList())).toList();
    }

    private List<FilePath> listFilesNames(ListObjectsV2Response resp) {
        return resp.contents().stream().map((x) -> new FilePath(Arrays.stream(x.key().split("/")).toList())).toList();
    }

    private void guaranteeFolderExists(FolderPath path) {
        for (int i = 0; i < path.pathElements.size() - 1; i++) {
            var resp = runListRequest(new FolderPath(path.pathElements.subList(0, i)));

            var directories = listSubDirectoriesNames(resp);
            int finalI = i;
            if(!directories.stream().anyMatch((x) -> x.pathElements.get(finalI + 1).equals(path.pathElements.get(finalI + 1)))) {
                runCreateFolderRequest(new FolderPath(path.pathElements.subList(0, i + 1)));
            }
        }
    }

    private Boolean isFolderExists(FolderPath path) {
        for (int i = 0; i < path.pathElements.size() - 1; i++) {
            var resp = runListRequest(new FolderPath(path.pathElements.subList(0, i)));

            var directories = listSubDirectoriesNames(resp);
            int finalI = i;
            if(!directories.stream().anyMatch((x) -> x.pathElements.get(finalI + 1).equals(path.pathElements.get(finalI + 1)))) {
                return false;
            }
        }

        return true;
    }

}