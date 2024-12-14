package fi.vm.yti.messaging.util;

import java.util.Date;

import org.springframework.http.HttpStatus;

import fi.vm.yti.messaging.dto.ErrorModel;
import fi.vm.yti.messaging.exception.YtiMessagingException;
import static fi.vm.yti.messaging.api.ApiConstants.*;
import static org.assertj.core.util.DateUtil.yesterday;

public interface ApplicationUtils {

    String TYPE_CODELIST = "codelist";

    String TYPE_CODE = "code";

    String TYPE_LIBRARY = "library";

    String TYPE_PROFILE = "profile";
    
    String TYPE_SCHEMA = "schema";
    
    String TYPE_CROSSWALK = "crosswalk";

    String TYPE_TERMINOLOGY = "terminology";

    String TYPE_COMMENTROUND = "commentround";

    String TYPE_COMMENTTHREAD = "commentthread";

    static String getApplicationByType(final String type) {
        switch (type) {
            case TYPE_LIBRARY:
            case TYPE_PROFILE:
            case TYPE_SCHEMA:
            case TYPE_CROSSWALK:
                return APPLICATION_DATAMODEL;
            default:
                throw new YtiMessagingException(new ErrorModel(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unknown type in resource: " + type));
        }
    }

    static Date createAfterDateForModifiedComparison() {
        final Date date = yesterday();
        date.setHours(5);
        date.setMinutes(0);
        return date;
    }
}
