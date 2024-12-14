package fi.vm.yti.messaging.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.vm.yti.messaging.api.Meta;
import fi.vm.yti.messaging.configuration.MessagingServiceProperties;
import fi.vm.yti.messaging.dto.IntegrationResourceDTO;
import fi.vm.yti.messaging.dto.IntegrationResponseDTO;
import fi.vm.yti.messaging.dto.ResourceDTO;
import fi.vm.yti.messaging.dto.UserDTO;
import fi.vm.yti.messaging.dto.UserNotificationDTO;
import fi.vm.yti.messaging.exception.NotFoundException;
import fi.vm.yti.messaging.exception.NotModifiedException;
import fi.vm.yti.messaging.service.EmailService;
import fi.vm.yti.messaging.service.IntegrationService;
import fi.vm.yti.messaging.service.NotificationService;
import fi.vm.yti.messaging.service.ResourceService;
import fi.vm.yti.messaging.service.UserService;
import static fi.vm.yti.messaging.api.ApiConstants.*;
import static fi.vm.yti.messaging.util.ApplicationUtils.*;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final String SUBSCRIPTION_TYPE_DAILY = "DAILY";

    private static final String LANGUAGE_FI = "fi";
    private static final String LANGUAGE_EN = "en";
    private static final String LANGUAGE_SV = "sv";
    private static final String LANGUAGE_UND = "und";

    private final UserService userService;
    private final ResourceService resourceService;
    private final EmailService emailService;
    private final IntegrationService integrationService;
    private final MessagingServiceProperties messagingServiceProperties;

    @Inject
    public NotificationServiceImpl(final UserService userService,
                                   final ResourceService resourceService,
                                   final EmailService emailService,
                                   final IntegrationService integrationService,
                                   final MessagingServiceProperties messagingServiceProperties) {
        this.userService = userService;
        this.resourceService = resourceService;
        this.emailService = emailService;
        this.integrationService = integrationService;
        this.messagingServiceProperties = messagingServiceProperties;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Europe/Helsinki")
    @Transactional
    public void sendAllNotifications() {
        LOG.info("Sending scheduled notifications!");
        final Map<String, IntegrationResourceDTO> updatedResourcesMap = fetchAndMapUpdatedResources();
        final Map<UUID, UserNotificationDTO> userNotifications = mapUserNotifications(updatedResourcesMap);
        sendUserNotifications(userNotifications);
    }

    @Transactional
    public void sendUserNotifications(final UUID userId) {
        final UserDTO user = userService.findById(userId);
        if (user != null && SUBSCRIPTION_TYPE_DAILY.equalsIgnoreCase(user.getSubscriptionType())) {
            final Map<String, IntegrationResourceDTO> updatedResourcesMap = fetchAndMapUpdatedResourcesForUser(userId);
            final UserNotificationDTO userNotification = mapUserNotificationResource(user, updatedResourcesMap);
            if (userNotification != null) {
                sendSingleUserNotifications(user.getId(), userNotification);
            } else {
                throw new NotModifiedException();
            }
        } else {
            throw new NotFoundException();
        }
    }

    private Map<String, IntegrationResourceDTO> fetchAndMapUpdatedResources() {
        return fetchAndMapUpdatedResourcesForUser(null);
    }

    private Map<String, IntegrationResourceDTO> fetchAndMapUpdatedResourcesForUser(final UUID userId) {
        final Map<String, IntegrationResourceDTO> updatedResourcesMap = new HashMap<>();
        final List<IntegrationResourceDTO> allUpdates = getUpdatedContainersForAllApplications(userId);
        allUpdates.forEach(updatedResource -> updatedResourcesMap.put(updatedResource.getUri(), updatedResource));
        return updatedResourcesMap;
    }

    private Map<UUID, UserNotificationDTO> mapUserNotifications(final Map<String, IntegrationResourceDTO> updatedResourcesMap) {
        final Map<UUID, UserNotificationDTO> userNotifications = new HashMap<>();
        final Set<UserDTO> users = userService.findAll();
        for (final UserDTO user : users) {
            if (SUBSCRIPTION_TYPE_DAILY.equalsIgnoreCase(user.getSubscriptionType())) {
                final UserNotificationDTO userNotificationDto = mapUserNotificationResource(user, updatedResourcesMap);
                if (userNotificationDto != null) {
                    userNotifications.put(user.getId(), userNotificationDto);
                }
            }
        }
        return userNotifications;
    }

    private UserNotificationDTO mapUserNotificationResource(final UserDTO user,
                                                            final Map<String, IntegrationResourceDTO> updatedResourcesMap) {
        final Set<ResourceDTO> resources = user.getResources();
        if (resources != null && !resources.isEmpty()) {
            final List<IntegrationResourceDTO> codeListUpdates = new ArrayList<>();
            final List<IntegrationResourceDTO> dataModelUpdates = new ArrayList<>();
            final List<IntegrationResourceDTO> terminologyUpdates = new ArrayList<>();
            final List<IntegrationResourceDTO> commentsUpdates = new ArrayList<>();
            for (final ResourceDTO resource : resources) {
                final String resourceUri = resource.getUri();
                if (updatedResourcesMap.keySet().contains(resourceUri)) {
                    switch (resource.getApplication()) {
                        case APPLICATION_DATAMODEL:
                            dataModelUpdates.add(updatedResourcesMap.get(resourceUri));
                            break;
                        default:
                            LOG.info("Unknown application type: " + resource.getApplication());
                    }
                }
            }
            Collections.sort(codeListUpdates);
            Collections.sort(dataModelUpdates);
            Collections.sort(terminologyUpdates);
            Collections.sort(commentsUpdates);
            UserNotificationDTO userNotificationDto = null;
            if (!codeListUpdates.isEmpty() || !dataModelUpdates.isEmpty() || !terminologyUpdates.isEmpty() || !commentsUpdates.isEmpty()) {
                userNotificationDto = new UserNotificationDTO(codeListUpdates, dataModelUpdates, terminologyUpdates, commentsUpdates);
            }
            return userNotificationDto;
        }
        return null;
    }

    private void sendUserNotifications(final Map<UUID, UserNotificationDTO> userNotifications) {
        userNotifications.keySet().forEach(userId -> sendSingleUserNotifications(userId, userNotifications.get(userId)));
    }

    private void sendSingleUserNotifications(final UUID userId,
                                             final UserNotificationDTO userNotificationDto) {
        final String message = constructMessage(userNotificationDto);
        emailService.sendMail(userId, message);
    }

    private String constructMessage(final UserNotificationDTO userNotificationDto) {
        final StringBuilder builder = new StringBuilder();
        builder.append("<body>");
        builder.append("Dear MSCR user,<br/>");
        builder.append("<br/>");
        builder.append("This is your summary of the changes to MSCR content that you have subscribed to.</a>.");
        builder.append("<br/>");
        final List<IntegrationResourceDTO> datamodelUpdates = userNotificationDto.getDatamodelResouces();
        if (!datamodelUpdates.isEmpty()) {
        	
            builder.append("<h3>Schemas</h3>");
            builder.append("<ul>");
            addContainerUpdates(APPLICATION_DATAMODEL, builder, datamodelUpdates, "schema");
            builder.append("</ul>");
            builder.append("<h3>Crosswalks</h3>");
            builder.append("<ul>");
            addContainerUpdates(APPLICATION_DATAMODEL, builder, datamodelUpdates, "crosswalk");
            builder.append("</ul>");
            
        }
        builder.append("<br/>");
        builder.append("<br/>");
        builder.append("This is an automatically generated message. Please, do not reply to this message.");
        builder.append("</body>");
        return builder.toString();
    }

    private void addContainerUpdates(final String applicationIdentifier,
                                     final StringBuilder builder,
                                     final List<IntegrationResourceDTO> resources,
                                     final String resourceType) {
        resources.stream().filter(new Predicate<IntegrationResourceDTO>() {

			@Override
			public boolean test(IntegrationResourceDTO t) {
				return t.getType().equalsIgnoreCase(resourceType);
			}
		}).forEach(resource -> {
			
            addResourceToBuilder(true, applicationIdentifier, builder, resource);
            
        });
    }

    private boolean isResourceNew(final IntegrationResourceDTO resource) {
        final Date created = resource.getCreated();
        final boolean isNew;
        if (created != null && created.after(createAfterDateForModifiedComparison())) {
            isNew = true;
        } else {
            isNew = false;
        }
        return isNew;
    }

    private void appendTotalSubResources(final StringBuilder builder,
                                         final int count) {
        builder.append("<li>");
        builder.append(count);
        builder.append(" muutosta yhteensä");
        builder.append("</li>");
    }

    private void appendInformationChanged(final StringBuilder builder,
                                          final String type,
                                          final boolean statusHasChanged) {
        builder.append("<li>");
        final String typeLabel = resolveLocalizationForType(type);
        builder.append(typeLabel);
        if (statusHasChanged) {
            builder.append(" tiedot ja tila ovat muuttuneet");
        } else {
            builder.append(" tiedot ovat muuttuneet");
        }
        builder.append("</li>");
    }

    private String resolveLocalizationForType(final String type) {
        if (type == null) {
            return "aineiston";
        }
        switch (type) {
            case TYPE_TERMINOLOGY:
                return "sanaston";
            case TYPE_CODELIST:
                return "koodiston";
            case TYPE_LIBRARY:
                return "tietokomponenttikirjaston";
            case TYPE_PROFILE:
                return "soveltamisprofiilin";
            case TYPE_COMMENTROUND:
                return "kommentointikierroksen";
            case TYPE_COMMENTTHREAD:
                return "kommentointiketjun";
            default:
                return "aineiston";
        }
    }

    private void addSubResourcesThatAreNew(final String applicationIdentifier,
                                           final StringBuilder builder,
                                           final Set<IntegrationResourceDTO> resources) {
        if (resources != null && !resources.isEmpty()) {
            builder.append("<li>uudet resurssit</li>");
            builder.append("<ul>");
            resources.forEach(resource -> addResourceToBuilder(true, applicationIdentifier, builder, resource));
            builder.append("</ul>");
        }
    }

    private void addSubResourcesWithStatusChanges(final String applicationIdentifier,
                                                  final StringBuilder builder,
                                                  final Set<IntegrationResourceDTO> resources) {
        if (resources != null && !resources.isEmpty()) {
            builder.append("<li>muuttuneet resurssit ja tilat</li>");
            builder.append("<ul>");
            resources.forEach(resource -> addResourceToBuilder(true, applicationIdentifier, builder, resource));
            builder.append("</ul>");
        }
    }

    private void addSubResourcesWithContentChanges(final String applicationIdentifier,
                                                   final StringBuilder builder,
                                                   final Set<IntegrationResourceDTO> resources) {
        if (resources != null && !resources.isEmpty()) {
            builder.append("<li>muuttuneet resurssit</li>");
            builder.append("<ul>");
            resources.forEach(resource -> addResourceToBuilder(true, applicationIdentifier, builder, resource));
            builder.append("</ul>");
        }
    }

    private void addResourceToBuilder(final boolean wrapToList,
                                      final String applicationIdentifier,
                                      final StringBuilder builder,
                                      final IntegrationResourceDTO resource) {
        final String prefLabel = getPrefLabelValueForEmail(resource.getPrefLabel());
        final String localName = resource.getLocalName();
        final String resourceUri = resource.getUri();
        builder.append("<li>");
        builder.append("<a href=");
        builder.append(encodeAndEmbedEnvironmentToUri(resourceUri));
        builder.append(">");
        if (prefLabel != null) {
            builder.append(prefLabel);
        } else if (localName != null) {
            builder.append(localName);
        } else {
            builder.append(resourceUri);
        }
        builder.append("</a>");
        final String status = resource.getStatus();
        if (status != null) {
            builder.append(": " + status);
        }
        builder.append(" - ");
        List<String> reasons = new ArrayList<String>();
        for(String reasonCode : resource.getReasonCodes()) {
        	reasons.add(getReasonString(reasonCode));
        }
        builder.append(String.join("/", reasons));
        builder.append("</li>");
    }

    private boolean isContainerType(final String type) {
        return TYPE_CODELIST.equalsIgnoreCase(type) || TYPE_COMMENTROUND.equalsIgnoreCase(type) || TYPE_LIBRARY.equalsIgnoreCase(type) || TYPE_PROFILE.equalsIgnoreCase(type) || TYPE_TERMINOLOGY.equalsIgnoreCase(type);
    }

    private String getPrefLabelValueForEmail(final Map<String, String> prefLabel) {
        if (prefLabel != null) {
            if (prefLabel.get(LANGUAGE_FI) != null) {
                return prefLabel.get(LANGUAGE_FI);
            } else if (prefLabel.get(LANGUAGE_EN) != null) {
                return prefLabel.get(LANGUAGE_EN);
            } else if (prefLabel.get(LANGUAGE_SV) != null) {
                return prefLabel.get(LANGUAGE_SV);
            } else if (prefLabel.get(LANGUAGE_UND) != null) {
                return prefLabel.get(LANGUAGE_UND);
            } else {
                return prefLabel.get(0);
            }
        }
        return null;
    }

    private String localizeStatus(final String status) {
        switch (status) {
            case "VALID":
                return "Voimassa oleva";
            case "INCOMPLETE":
                return "Keskeneräinen";
            case "DRAFT":
                return "Luonnos";
            case "SUGGESTED":
                return "Ehdotus";
            case "SUPERSEDED":
                return "Korvattu";
            case "RETIRED":
                return "Poistettu käytöstä";
            case "INVALID":
                return "Virheellinen";
            case "INPROGRESS":
                return "Käynnissä";
            case "AWAIT":
                return "Odottaa";
            case "ENDED":
                return "Päättynyt";
            case "CLOSED":
                return "Suljettu";
            default:
                return status;
        }
    }
    
    private String getReasonString(String code) {
    	switch (code) {
		case "1":
			return "Content changed";
		case "2":
			return "Status changed";
		case "3":
			return "Source schema content changed";
		case "4":
			return "Target schema content changed";
		case "5":
			return "Source schema has new revision";
		case "6":
			return "Target schema has new revision";

		default:
			return "";
		}
    }

    private String encodeAndEmbedEnvironmentToUri(final String uri) {
        final String env = messagingServiceProperties.getEnv();
        final String encodedUri = encodeUri(uri);
        if ("prod".equalsIgnoreCase(env)) {
            return encodedUri;
        } else {
            return encodedUri + "?env=" + env;
        }
    }

    private String encodeUri(final String uri) {
        return uri.replace("#", "%23");
    }

    private List<IntegrationResourceDTO> getUpdatedContainersForAllApplications(final UUID userId) {
        final List<IntegrationResourceDTO> updatedResources = new ArrayList<>();
        addUpdatedContainersForApplication(APPLICATION_DATAMODEL, updatedResources, userId);
        return updatedResources;
    }

    private void addUpdatedContainersForApplication(final String applicationIdentifier,
                                                    final List<IntegrationResourceDTO> updatedResources,
                                                    final UUID userId) {
        final List<IntegrationResourceDTO> resources;
        if (userId != null) {
            resources = getUpdatedApplicationContainersForUserId(applicationIdentifier, userId);
        } else {
            resources = getUpdatedApplicationContainers(applicationIdentifier);
        }
        if (resources != null && !resources.isEmpty()) {
            updatedResources.addAll(resources);
        }
    }

    private List<IntegrationResourceDTO> getUpdatedApplicationContainersForUserId(final String applicationIdentifier,
                                                                                  final UUID userId) {
        final Set<String> containerUris = resourceService.getResourceUrisForApplicationAndUserId(applicationIdentifier, userId);
        if (containerUris != null && !containerUris.isEmpty()) {
            return getUpdatedApplicationContainersWithUris(applicationIdentifier, containerUris, true);
        }
        return null;
    }

    private List<IntegrationResourceDTO> getUpdatedApplicationContainers(final String applicationIdentifier) {
        final Set<String> containerUris = resourceService.getResourceUrisForApplication(applicationIdentifier);
        if (containerUris != null && !containerUris.isEmpty()) {
            return getUpdatedApplicationContainersWithUris(applicationIdentifier, containerUris);
        }
        return null;
    }

    private List<IntegrationResourceDTO> getUpdatedApplicationContainersWithUris(final String applicationIdentifier,
                                                                                 final Set<String> containerUris) {
        return getUpdatedApplicationContainersWithUris(applicationIdentifier, containerUris, false);

    }

    private List<IntegrationResourceDTO> getUpdatedApplicationContainersWithUris(final String applicationIdentifier,
                                                                                 final Set<String> containerUris,
                                                                                 final boolean getLatest) {
        LOG.info("Fetching containers for: " + applicationIdentifier);
        final boolean fetchDateRangeChanges = true;
        if (containerUris != null && !containerUris.isEmpty()) {
            final IntegrationResponseDTO integrationResponse = integrationService.getIntegrationContainers(applicationIdentifier, containerUris, fetchDateRangeChanges, getLatest);
            final List<IntegrationResourceDTO> containers = integrationResponse.getResults();
            if (containers != null && !containers.isEmpty()) {
                LOG.info("Found " + containers.size() + " for application: " + applicationIdentifier);
                // TODO: Remove container filtering once Terminology adds support for contentModified timestamping
                /*
                final Set<IntegrationResourceDTO> containersToFilter = new HashSet<>();
                containers.forEach(container -> {
                    final Date contentModified = container.getContentModified();
                    final Date contentModifiedComparisonDate = createAfterDateForModifiedComparison();
                    final IntegrationResponseDTO integrationResponseForResources;
                    if ((contentModified != null && (contentModified.after(contentModifiedComparisonDate) || contentModified.equals(contentModifiedComparisonDate)))) {
                        LOG.info("Container: " + container.getUri() + " has content that has been modified lately, fetching resources.");
                        integrationResponseForResources = integrationService.getIntegrationResources(applicationIdentifier, container.getUri(), true, getLatest);
                        LOG.info("Resources for " + applicationIdentifier + " have " + integrationResponseForResources.getResults().size() + " updates.");
                        if (integrationResponseForResources.getResults() != null && !integrationResponseForResources.getResults().isEmpty()) {
                            container.setSubResourceResponse(integrationResponseForResources);
                        } else if (!container.getModified().after(createAfterDateForModifiedComparison())) {
                            containersToFilter.add(container);
                        }
                    }
                });
                containers.removeAll(containersToFilter);
                */
                return containers;
            } else {
                LOG.info("No containers have updates for " + applicationIdentifier);
            }
        }
        return null;
    }
}
