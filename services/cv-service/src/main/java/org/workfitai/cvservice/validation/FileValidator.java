package org.workfitai.cvservice.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.cvservice.errors.InvalidDataException;

import static org.workfitai.cvservice.constant.ErrorConst.*;

public class FileValidator {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 5MB
    private static final String ALLOWED_CONTENT_TYPE = "application/pdf";

    public static void validate(MultipartFile file) throws InvalidDataException {
        if (file == null || file.isEmpty()) {
            throw new InvalidDataException(CV_EMPTY_FILE, HttpStatus.NOT_FOUND);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidDataException(CV_FILE_EXCEED, HttpStatus.REQUEST_ENTITY_TOO_LARGE);
        }

        String contentType = file.getContentType();
        if (!ALLOWED_CONTENT_TYPE.equals(contentType)) {
            throw new InvalidDataException(CV_FILE_WRONG_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
    }
}