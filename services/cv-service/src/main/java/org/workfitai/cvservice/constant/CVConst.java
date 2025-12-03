package org.workfitai.cvservice.constant;


public class CVConst {


    // ---------------- URL / File Pattern ----------------
    public static final String URL_PATTERN = "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$";
    public static final String PDF_FILE_PATTERN = "^[a-zA-Z0-9._-]+\\.pdf$";


    // ---------------- System / User ----------------
    public static final String SYSTEM_ACCOUNT = "system";
    public static final String DEFAULT_LANGUAGE = "en";
    public static final String ANONYMOUS_USER = "anonymous";

    // ---------------- Default Values ----------------
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 10;


    // ---------------- Filter Keys ----------------
    public static final String FILTER_TEMPLATE_TYPE = "templateType";
}

