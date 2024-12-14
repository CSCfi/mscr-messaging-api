package fi.vm.yti.messaging.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlRootElement
@XmlType(propOrder = { "uri", "type", "prefLabel", "description", "localName", "status", "created", "modified", "statusModified", "contentModified", "type", "subResourceResponse" })
@Schema(name = "Integration resource", description = "Integration resource DTO that represents data for one single Container or Resource for integration use.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntegrationResourceDTO implements Serializable, Comparable<IntegrationResourceDTO> {

    private static final long serialVersionUID = 1L;

    private Map<String, String> prefLabel;
    private Map<String, String> description;
    private String localName;
    private String uri;
    private String status;
    private Date created;
    private Date modified;
    private Date statusModified;
    private Date contentModified;
    private String type;
    private IntegrationResponseDTO subResourceResponse;
    private String[] reasonCodes;

    public IntegrationResourceDTO() {
        prefLabel = new HashMap<>();
        description = new HashMap<>();
    }

    
    public String[] getReasonCodes() {
		return reasonCodes;
	}


	public void setReasonCodes(String[] reasonCodes) {
		this.reasonCodes = reasonCodes;
	}


	public String getUri() {
        return uri;
    }

    public void setUri(final String uri) {
        this.uri = uri;
    }

    public Map<String, String> getPrefLabel() {
        return prefLabel;
    }

    public void setPrefLabel(final Map<String, String> prefLabel) {
        this.prefLabel = prefLabel;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public void setDescription(final Map<String, String> description) {
        this.description = description;
    }

    public String getPrefLabel(final String language) {
        String prefLabelValue = null;
        if (this.prefLabel != null && !this.prefLabel.isEmpty()) {
            prefLabelValue = this.prefLabel.get(language);
            if (prefLabelValue == null) {
                prefLabelValue = this.prefLabel.get("sv");
            }
            if (prefLabelValue == null) {
                prefLabelValue = this.prefLabel.get("en");
            }
            if (prefLabelValue == null) {
                prefLabelValue = this.prefLabel.get(0);
            }
        }
        return prefLabelValue;
    }

    public String getLocalName() {
        return localName;
    }

    public void setLocalName(final String localName) {
        this.localName = localName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    @Schema(format = "dateTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    public Date getCreated() {
        if (created != null) {
            return new Date(created.getTime());
        }
        return null;
    }

    public void setCreated(final Date created) {
        if (created != null) {
            this.created = new Date(created.getTime());
        } else {
            this.created = null;
        }
    }

    @Schema(format = "dateTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    public Date getModified() {
        if (modified != null) {
            return new Date(modified.getTime());
        }
        return null;
    }

    public void setModified(final Date modified) {
        if (modified != null) {
            this.modified = new Date(modified.getTime());
        } else {
            this.modified = null;
        }
    }

    @Schema(format = "dateTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    public Date getStatusModified() {
        return statusModified;
    }

    public void setStatusModified(final Date statusModified) {
        this.statusModified = statusModified;
    }

    @Schema(format = "dateTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    public Date getContentModified() {
        return contentModified;
    }

    public void setContentModified(final Date contentModified) {
        this.contentModified = contentModified;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public IntegrationResponseDTO getSubResourceResponse() {
        return subResourceResponse;
    }

    public void setSubResourceResponse(final IntegrationResponseDTO subResourceResponse) {
        this.subResourceResponse = subResourceResponse;
    }

    @Override
    public int compareTo(final IntegrationResourceDTO integrationResource) {
        if (getPrefLabel() == null && integrationResource.getPrefLabel() == null) {
            return 0;
        }
        if (getPrefLabel("fi") != null && integrationResource.getPrefLabel("fi") != null) {
            return getPrefLabel("fi").compareToIgnoreCase(integrationResource.getPrefLabel("fi"));
        } else if (getPrefLabel("und") != null && integrationResource.getPrefLabel("und") != null) {
            return getPrefLabel("und").compareToIgnoreCase(integrationResource.getPrefLabel("und"));
        }
        return 0;
    }
}
