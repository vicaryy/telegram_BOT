package org.vicary.service.downloader;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.vicary.api_request.InputFile;
import org.vicary.api_request.edit_message.EditMessageText;
import org.vicary.command.YtDlpCommand;
import org.vicary.entity.InstagramFileEntity;
import org.vicary.exception.DownloadedFileNotFoundException;
import org.vicary.exception.InvalidBotRequestException;
import org.vicary.info.DownloaderInfo;
import org.vicary.model.FileInfo;
import org.vicary.model.FileRequest;
import org.vicary.model.FileResponse;
import org.vicary.service.Converter;
import org.vicary.service.FileManager;
import org.vicary.service.file_service.InstagramFileService;
import org.vicary.service.mapper.FileInfoMapper;
import org.vicary.service.quick_sender.QuickSender;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InstagramDownloader implements Downloader {
    private final static Logger logger = LoggerFactory.getLogger(TwitterDownloader.class);

    private final QuickSender quickSender;

    private final DownloaderInfo info;

    private final YtDlpCommand commands;

    private final InstagramFileService instagramFileService;

    private final FileInfoMapper mapper;

    private final Gson gson;

    private final Converter converter;

    private final List<String> availableExtensions = List.of("mp4");

    @Override
    public FileResponse download(FileRequest request) throws IllegalArgumentException, NoSuchElementException, IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(new File(commands.getDownloadDestination()));
        EditMessageText editMessageText = request.getEditMessageText();

        // SENDING INFO ABOUT CONNECTING TO TWITTER
        quickSender.editMessageText(editMessageText, editMessageText.getText() + info.getConnectingToInstagram());

        // GETTING TWITTER FILE INFO
        FileResponse response = getFileInfo(request, processBuilder);

        // CHECKS IF FILE ALREADY EXISTS IN REPOSITORY
        response = getFileFromRepository(response);
        if (response.getDownloadedFile() != null)
            return response;


        // IF FILE DOES NOT EXIST IN REPOSITORY THEN DOWNLOAD
        String fileSizeInProcess = null;
        String fileName = FileManager.getFileNameFromTitle(response.getTitle(), response.getExtension());
        String filePath = commands.getDownloadDestination() + fileName;
        boolean fileDownloaded = false;
        editMessageText.setText(editMessageText.getText() + info.getFileDownloading());

        processBuilder.command(commands.getDownloadInstagramFile(fileName, response.getURL(), response.getMultiVideoNumber()));
        Process process = processBuilder.start();
        // SENDING INFO ABOUT DOWNLOADING FILE
        logger.info("[download] Downloading Instagram file '{}'", response.getId());
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!fileDownloaded) {
                    updateDownloadProgressInMessageText(request.getEditMessageText(), line);
                    if (request.getEditMessageText().getText().contains("100%")) {
                        logger.info("[download] Successfully downloaded file '{}'", response.getId());
                        fileDownloaded = true;
                    }
                }

                if (fileSizeInProcess == null) {
                    fileSizeInProcess = FileManager.getFileSizeInProcess(line);
                    if (fileSizeInProcess != null && !FileManager.checkFileSizeProcess(fileSizeInProcess)) {
                        process.destroy();
                        throw new InvalidBotRequestException(
                                info.getFileTooBig(),
                                String.format("Size of file '%s' is too big. File Size: '%s'", response.getId(), fileSizeInProcess));
                    }
                }

                if (isFileSizeTooBigInProcess(line)) {
                    process.destroy();
                    throw new InvalidBotRequestException(
                            info.getFileTooBig(),
                            String.format("Size of file '%s' is too big. File Size: '%s'", response.getId(), converter.bytesToMB(getFileSizeInProcess(line))));
                }
            }
        }
        File downloadedFile = new File(filePath);
        if (downloadedFile.exists()) {
            long fileSize = downloadedFile.length();
            if (!FileManager.isFileSizeValid(fileSize)) {
                throw new InvalidBotRequestException(
                        info.getFileTooBig(),
                        String.format("Size of file '%s' is too big. File Size: '%s'", response.getId(), converter.bytesToMB(fileSize)));
            }
            response.setSize(fileSize);
            response.setDownloadedFile(InputFile.builder()
                    .file(downloadedFile)
                    .build());
        } else {
            throw new DownloadedFileNotFoundException(
                    info.getErrorInDownloading(),
                    String.format("File '%s' has not been downloaded", response.getId()));
        }
        response.setEditMessageText(editMessageText);
        return response;
    }

    @Override
    public List<String> getAvailableExtensions() {
        return availableExtensions;
    }

    @Override
    public String getServiceName() {
        return "instagram";
    }

    public FileResponse getFileInfo(FileRequest request, ProcessBuilder processBuilder) throws IOException {
        String fileInfoInJson = "";
        int amountOfFiles = 0;
        int multiVideoNumber = request.getMultiVideoNumber() == 0 ? 1 : request.getMultiVideoNumber();
        boolean specify = request.getMultiVideoNumber() != 0;
        final int multiVideoMaxAmount = 15;

        processBuilder.command(commands.getDownloadFileInfo(request.getURL()));
        Process process = processBuilder.start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                amountOfFiles++;

                FileInfo fileInfo = gson.fromJson(line, FileInfo.class);
                String webpageURL = fileInfo.getURL();
                if (webpageURL == null || !webpageURL.contains("instagram.com/")) {
                    throw new InvalidBotRequestException(
                            info.getNoVideo(),
                            String.format("No video in Instagram URL '%s' and other service URL in description.", request.getURL()));
                }

                if (amountOfFiles > 1 && !specify) {
                    throw new InvalidBotRequestException(
                            info.getMultiVideo(),
                            String.format("Instagram URL '%s' is a multi-video link and user do not specify which video he want.", request.getURL()));
                }

                if (amountOfFiles > multiVideoMaxAmount) {
                    throw new InvalidBotRequestException(
                            info.getMultiVideoAmountTooHigh(),
                            String.format("Amount of multi-video Instagram URL '%s' is too high, more than 15.", request.getURL()));
                }

                if (amountOfFiles == multiVideoNumber) {
                    fileInfoInJson = line;
                }
            }
        } catch (IOException ex) {
            process.destroy();
            throw new IOException(ex.getMessage());
        }

        FileInfo fileInfo = gson.fromJson(fileInfoInJson, FileInfo.class);

        if (fileInfo == null) {
            throw new InvalidBotRequestException(
                    info.getNoVideo(),
                    String.format("No video in Instagram URL '%s'", request.getURL()));
        }

        if (fileInfoInJson.isEmpty() && multiVideoNumber > amountOfFiles) {
            throw new InvalidBotRequestException(
                    info.getReceivedWrongNumberInMultiVideo(amountOfFiles, multiVideoNumber),
                    String.format("No video in multi-video Instagram URL '%s'", request.getURL()));
        }

        if (fileInfo.isLive()) {
            throw new InvalidBotRequestException(
                    info.getLiveVideo(),
                    String.format("Live video in TikTok URL '%s'.", request.getURL()));
        }


        FileResponse fileResponse = mapper.map(fileInfo);
        fileResponse.setMultiVideoNumber(multiVideoNumber);
        fileResponse.setExtension(request.getExtension());
        fileResponse.setPremium(request.isPremium());
        return fileResponse;
    }

    public FileResponse getFileFromRepository(FileResponse response) {
        Optional<InstagramFileEntity> instagramFileEntity = instagramFileService.findByInstagramId(response.getId());

        if (instagramFileEntity.isPresent() && converter.MBToBytes(instagramFileEntity.get().getSize()) < 20000000) {
            InputFile file = InputFile.builder()
                    .fileId(instagramFileEntity.get().getFileId())
                    .build();
            response.setDownloadedFile(file);
            response.setSize(converter.MBToBytes(instagramFileEntity.get().getSize()));
        }
        return response;
    }

    public boolean isFileSizeTooBigInProcess(String line) {
        return line.startsWith("[download] File is larger than max-filesize");
    }

    public Long getFileSizeInProcess(String line) {
        long size = 0;
        if (line.startsWith("[download] File is larger than max-filesize")) {
            String[] arraySplit = line.split("\\(");
            size = Arrays.stream(arraySplit[1].split(" "))
                    .findFirst()
                    .map(Long::parseLong)
                    .orElse(0L);
        }
        return size;
    }

    public void updateDownloadProgressInMessageText(EditMessageText editMessageText, String line) {
        String progress = FileManager.getDownloadFileProgressInProcess(line);
        if (progress != null) {
            String oldText = editMessageText.getText();
            String[] splitOldText = oldText.split(" ");
            StringBuilder newText = new StringBuilder();

            for (String s : splitOldText)
                if (s.equals(splitOldText[splitOldText.length - 1]))
                    newText.append("\\[").append(progress).append("\\]_");
                else
                    newText.append(s).append(" ");

            if (!oldText.contentEquals(newText))
                quickSender.editMessageText(editMessageText, newText.toString());
        }
    }
}
