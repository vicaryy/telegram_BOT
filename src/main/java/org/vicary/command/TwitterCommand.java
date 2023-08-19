package org.vicary.command;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.vicary.model.twitter.TwitterFileResponse;

@Component
public class TwitterCommand {
    private final String ytDlpCommand = "yt-dlp";
    private final String pathCommand = "-o";
    @Getter
    private final String path = "/Users/vicary/desktop/folder/";
    private final String embedThumbnailCommand = "--embed-thumbnail";
    private final String maxFileSizeCommand = "--max-filesize";
    private final String maxFileSize = "45M";
    private final String netrcCommand = "--netrc";
    private final String fileInfoCommand = "-j";


    public String[] downloadFile(String url, String fileName) {
        String[] command = {ytDlpCommand, netrcCommand, embedThumbnailCommand, maxFileSizeCommand, maxFileSize, pathCommand, fileName, url};
        return command;
    }

    public String[] downloadFileInfo(String twitterUrl) {
        String[] command = {ytDlpCommand, netrcCommand, fileInfoCommand, twitterUrl};
        return command;
    }
}