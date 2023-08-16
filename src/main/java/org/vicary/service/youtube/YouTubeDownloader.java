package org.vicary.service.youtube;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.vicary.api_request.InputFile;
import org.vicary.api_request.edit_message.EditMessageText;
import org.vicary.entity.YouTubeFileEntity;
import org.vicary.format.MarkdownV2;
import org.vicary.model.YouTubeFileInfo;
import org.vicary.model.YouTubeFileRequest;
import org.springframework.stereotype.Service;
import org.vicary.model.YouTubeFileResponse;
import org.vicary.service.RequestService;
import org.vicary.service.YouTubeFileService;
import org.vicary.service.mapper.YouTubeFileMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class YouTubeDownloader {
    private final static Logger logger = LoggerFactory.getLogger(YouTubeDownloader.class);

    private final RequestService requestService;

    private final YouTubeFileService youTubeFileService;

    private final YouTubeFileMapper mapper;

    private final YouTubeCommand commands;

    private final YouTubeInfo info;

    private final Gson gson;

    public YouTubeFileResponse download(YouTubeFileRequest request) throws WebClientRequestException, WebClientRequestException, IllegalArgumentException, NoSuchElementException, IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        boolean fileDownloaded = false;
        boolean fileConverted = false;
        boolean premium = request.getPremium();
        EditMessageText editMessageText = request.getEditMessageText();
        String extension = request.getExtension();

        String filePath;
        String fileSize = null;

        // getting youtube file info
        editMessageText = sendEditMessageText(editMessageText, editMessageText.getText() + info.getConnectingToYoutubeInfo());
        YouTubeFileResponse response = getFileInfo(request, processBuilder);
        response.setExtension(extension);
        response.setPremium(request.getPremium());


        // checks if file already exists in repository
        response = getFileFromRepository(response);

        // if file is not in repo then download FILE
        if (response.getDownloadedFile() == null) {
            filePath = String.format("%s%s.%s", commands.getPath(), response.getTitle(), extension);
            editMessageText.setText(editMessageText.getText() + info.getFileDownloadingInfo());
            processBuilder.command(commands.getFileCommand(response));
            Process process = processBuilder.start();
            logger.info("[download] Downloading YouTube file '{}'", response.getYoutubeId());

            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {

                    if (!fileDownloaded) {
                        editMessageText = updateMessageTextDownload(request.getEditMessageText(), line);
                        if (request.getEditMessageText().getText().contains("100%")) {
                            logger.info("[download] Successfully downloaded file '{}'", response.getYoutubeId());
                            fileDownloaded = true;
                        }
                    }

                    if (!fileConverted && isFileConverting(line)) {
                        editMessageText = sendEditMessageText(editMessageText, editMessageText.getText() + info.getConvertingInfo(extension));
                        logger.info("[convert] Successfully converted to {} file '{}'", response.getExtension(), response.getYoutubeId());
                        fileConverted = true;
                    }

                    if (fileSize == null) {
                        fileSize = getFileSize(line);
                        if (fileSize != null && !checkFileSizeProcessBuilder(fileSize)) {
                            editMessageText = sendEditMessageText(editMessageText, info.getFileTooBigInfo() + info.getUpTo50MBInfo());
                            logger.warn("Size of file '{}' is too big. File size: {}", response.getYoutubeId(), fileSize);
                            process.destroy();
                        }
                    }
                }
            }
            if (new File(filePath).exists()) {
                checkFileSize(new File(filePath).length(), editMessageText, response.getYoutubeId());
                response.setSize(new File(filePath).length());
                String oldFilePath = filePath;
                filePath = correctFilePath(filePath, extension, response.getYoutubeId());
                if (!oldFilePath.equals(filePath)) {
                    editMessageText = sendEditMessageText(editMessageText, editMessageText.getText() + info.getRenamingInfo());
                }
                File downladedFile = new File(filePath);
                response.setDownloadedFile(InputFile.builder()
                        .file(downladedFile)
                        .build());
            }
        }

        // downloading thumbnail
        final String thumbnailName = generateUniqueName() + ".jpg";
        String thumbnailPath = null;
        editMessageText.setText(editMessageText.getText() + info.getThumbDownloadingInfo());

        processBuilder.command(commands.getThumbnailCommand(thumbnailName, request.getYoutubeId()));
        Process process = processBuilder.start();

        logger.info("[download] Downloading thumbnail to file '{}'", response.getYoutubeId());
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = br.readLine()) != null) {
                editMessageText = updateMessageTextDownload(editMessageText, line);

                if (line.equals("[download] Destination: /Users/vicary/desktop/folder/" + thumbnailName))
                    thumbnailPath = commands.getPath() + thumbnailName;
            }
        }

        if (thumbnailPath != null) {
            logger.info("[download] Successfully downloaded thumbnail to file '{}'", response.getYoutubeId());
            response.setThumbnail(InputFile.builder()
                    .file(new File(thumbnailPath))
                    .isThumbnail(true)
                    .build());
        }

        // setting other stuff
        response.setEditMessageText(editMessageText);
        response.setExtension(extension);
        response.setPremium(premium);
        return response;
    }

    public YouTubeFileResponse getFileFromRepository(YouTubeFileResponse response) {
        Optional<YouTubeFileEntity> youTubeFileEntity = youTubeFileService.findByYoutubeIdAndExtensionAndQuality(
                response.getYoutubeId(),
                response.getExtension(),
                response.getPremium() ? "premium" : "standard");

        if (youTubeFileEntity.isPresent() && convertMBToBytes(youTubeFileEntity.get().getSize()) < 20000000) {
            InputFile file = InputFile.builder()
                    .fileId(youTubeFileEntity.get().getFileId())
                    .build();
            response.setDownloadedFile(file);
            response.setSize(convertMBToBytes(youTubeFileEntity.get().getSize()));
        }
        return response;
    }

    public YouTubeFileResponse getFileInfo(YouTubeFileRequest request, ProcessBuilder processBuilder) throws IOException {
        StringBuilder sb = new StringBuilder();

        processBuilder.command(commands.getFileDataCommand(request.getYoutubeId()));
        Process process = processBuilder.start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null)
                sb.append(line);
        } catch (IOException ex) {
            throw new IOException(ex.getMessage());
        }

        YouTubeFileInfo youTubeFileInfo = gson.fromJson(sb.toString(), YouTubeFileInfo.class);
        return mapper.map(youTubeFileInfo);
    }

    public EditMessageText updateMessageTextDownload(EditMessageText editMessageText, String line) {
        String progress = getDownloadProgress(line);
        if (progress != null) {
            String oldText = editMessageText.getText();
            String[] splitOldText = oldText.split(" ");
            StringBuilder newText = new StringBuilder();

            for (String s : splitOldText)
                if (s.equals(splitOldText[splitOldText.length - 1]))
                    newText.append("\\[" + progress + "\\]_");
                else
                    newText.append(s + " ");

            if (!oldText.contentEquals(newText))
                sendEditMessageText(editMessageText, newText.toString());
        }
        return editMessageText;
    }

    public EditMessageText sendEditMessageText(EditMessageText editMessageText, String text) {
        editMessageText.setText(text);
        requestService.sendRequestAsync(editMessageText);
        return editMessageText;
    }

    public Boolean isFileConverting(String line) {
        return line.startsWith("[ExtractAudio] Destination: /Users/vicary/desktop/folder/");
    }

    private Long convertMBToBytes(String MB) {
        MB = MB.replaceFirst("MB", "");
        MB = MB.replaceFirst(",", ".");
        double Megabytes = Double.parseDouble(MB);
        return (long) (Megabytes * (1024 * 1024));
    }

    public String getDownloadProgress(String line) {
        if (line.contains("[download]")) {
            String[] s = line.split(" ");
            for (String a : s)
                if (a.contains("%"))
                    return MarkdownV2.apply(a).get();
        }
        return null;
    }


    public String correctFilePath(String filePath, String extension, String youtubeId) throws IOException {
        int maxFileNameLength = 63;
        String oldFileName = filePath.replaceFirst(commands.getPath(), "");
        String newFileName = oldFileName;

        newFileName = newFileName.replaceAll("&|⧸⧹", "and");
        newFileName = newFileName.replaceAll("[/⧸||｜–\\\\]", "-");

        if (newFileName.length() > maxFileNameLength)
            newFileName = newFileName.substring(0, 59) + "." + extension;

        if (newFileName.equals(oldFileName))
            return filePath;

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(commands.getRenameFileCommand(oldFileName, newFileName));
        processBuilder.directory(new File(commands.getPath()));
        Process process = processBuilder.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (br.readLine() != null)
                System.out.println(br.readLine());
        }
        logger.info("[rename] Renaming file '{}' to: {}", youtubeId, newFileName);
        return commands.getPath() + newFileName;
    }

    public boolean checkFileSizeProcessBuilder(String fileSize) {
        if (fileSize.endsWith("KiB"))
            return true;
        if (!fileSize.endsWith("MiB"))
            return false;

        StringBuilder sb = new StringBuilder();
        sb.append(0);
        for (char c : fileSize.toCharArray()) {
            if (c == '.')
                break;
            sb.append(c);
        }
        return Integer.parseInt(sb.toString()) <= 45;
    }

    public void checkFileSize(Long size, EditMessageText editMessageText, String youtubeId) {
        long fileSize = size / (1024 * 1024);
        if (fileSize > 50) {
            sendEditMessageText(editMessageText, info.getFileTooBigInfo() + info.getUpTo50MBInfo());
            logger.warn("Size of file '{}' is too big. File size: {}MB", youtubeId, fileSize);
            throw new IllegalArgumentException("File size cannot be more than 50MB." +
                                               "\n Your file size: " + fileSize + "MB.");
        }
    }

    public String generateUniqueName() {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.setLength(0);
        for (int i = 0; i < 10; i++)
            stringBuilder.append(ThreadLocalRandom.current().nextInt(0, 10));

        return stringBuilder.toString();
    }

    public String getFileSize(String line) {
        if (line.contains("[download]")) {
            String[] s = line.split(" ");
            for (String a : s)
                if (a.contains("MiB") || a.contains("KiB"))
                    return a;
        }
        return null;
    }

    public void deleteFile(InputFile inputFile) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        String fileName = inputFile.getFile().getName();
        processBuilder.command(commands.getRemoveFileCommand(fileName));
        processBuilder.directory(new File(commands.getPath()));

        processBuilder.start();
        logger.info("[delete] Deleting original file {}", fileName);
    }
}