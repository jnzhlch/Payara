# CLAUDE.md - JavaMail Runtime

This file provides guidance for working with the `appserver/resources/javamail/javamail-runtime` module - Runtime deployer and annotation support for JavaMail resources.

## Module Overview

The javamail-runtime module provides the runtime deployer for JavaMail session resources and CDI annotation handlers for @MailSessionDefinition/@MailSessionDefinitions.

**Key Components:**
- **MailResourceDeployer** - Runtime deployer for mail resources
- **MailNamingObjectFactory** - JNDI ObjectFactory for creating mail sessions
- **Annotation Handlers** - CDI handlers for @MailSessionDefinition annotations

## Build Commands

```bash
# Build javamail-runtime module
mvn -DskipTests clean package -f appserver/resources/javamail/javamail-runtime/pom.xml
```

## Module Contents

```
javamail-runtime/
└── src/main/java/org/glassfish/resources/javamail/
    ├── deployer/
    │   └── MailResourceDeployer.java
    ├── beans/
    │   └── MailBean.java
    ├── naming/
    │   └── MailNamingObjectFactory.java
    └── annotation/handler/
        ├── MailSessionDefinitionHandler.java
        └── MailSessionDefinitionsHandler.java
```

## MailResourceDeployer

### Deployment Process

```java
@Service
@ResourceDeployerInfo(MailResource.class)
@Singleton
public class MailResourceDeployer extends GlobalResourceDeployer
        implements ResourceDeployer {

    @Inject
    private ResourceNamingService namingService;

    public void installMailResource(MailBean mailResource, ResourceInfo resourceInfo) {
        // Create MailConfiguration from MailBean
        MailConfiguration config = new MailConfiguration(mailResource);

        // Create JNDI Reference
        Reference ref = new Reference(jakarta.mail.Session.class.getName(),
                                     MailNamingObjectFactory.class.getName(), null);
        SerializableObjectRefAddr serializableRefAddr =
            new SerializableObjectRefAddr("jndiName", config);
        ref.add(serializableRefAddr);

        // Publish to JNDI
        namingService.publishObject(resourceInfo, ref, true);
    }
}
```

### MailBean to MailConfiguration

```java
public static MailBean toMailBean(MailResource mailResourceConfig, ResourceInfo resourceInfo) {
    MailBean mailResource = new MailBean(resourceInfo);

    mailResource.setStoreProtocol(mailResourceConfig.getStoreProtocol());
    mailResource.setStoreProtocolClass(mailResourceConfig.getStoreProtocolClass());
    mailResource.setTransportProtocol(mailResourceConfig.getTransportProtocol());
    mailResource.setTransportProtocolClass(mailResourceConfig.getTransportProtocolClass());

    // Expand system properties in values
    mailResource.setMailHost(TranslatedConfigView.expandValue(mailResourceConfig.getHost()));
    mailResource.setUsername(TranslatedConfigView.expandValue(mailResourceConfig.getUser()));
    mailResource.setPassword(TranslatedConfigView.expandValue(mailResourceConfig.getPassword()));
    mailResource.setMailFrom(TranslatedConfigView.expandValue(mailResourceConfig.getFrom()));

    mailResource.setAuth(Boolean.valueOf(mailResourceConfig.getAuth()));
    mailResource.setDebug(Boolean.valueOf(mailResourceConfig.getDebug()));

    // Add custom properties
    for (Property property : mailResourceConfig.getProperty()) {
        ResourceProperty rp = new ResourcePropertyImpl(property.getName(), property.getValue());
        mailResource.addProperty(rp);
    }

    return mailResource;
}
```

## MailNamingObjectFactory

### JNDI ObjectFactory

```java
public class MailNamingObjectFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                   Hashtable<?, ?> environment) throws Exception {
        Reference ref = (Reference) obj;

        // Extract MailConfiguration from SerializableObjectRefAddr
        SerializableObjectRefAddr sor = (SerializableObjectRefAddr) ref.get(0);
        MailConfiguration config = (MailConfiguration) sor.getContent();

        // Create Jakarta Mail Session
        return createSession(config);
    }

    private Session createSession(MailConfiguration config) {
        Properties props = new Properties();

        // Store protocol
        if (config.getStoreProtocol() != null) {
            props.put("mail.store.protocol", config.getStoreProtocol());
        }

        // Transport protocol
        if (config.getTransportProtocol() != null) {
            props.put("mail.transport.protocol", config.getTransportProtocol());
        }

        // Mail host
        if (config.getMailHost() != null) {
            props.put("mail.host", config.getMailHost());
            props.put("mail.smtp.host", config.getMailHost());
            props.put("mail.imap.host", config.getMailHost());
        }

        // Custom properties
        for (ResourceProperty prop : config.getProperties()) {
            props.put(prop.getName(), prop.getValue());
        }

        // Authentication
        if (config.isAuth()) {
            Authenticator auth = new MailSessionAuthenticator(
                config.getUsername(), config.getPassword());
            return Session.getInstance(props, auth);
        }

        return Session.getInstance(props);
    }
}
```

## MailSessionAuthenticator

```java
public class MailSessionAuthenticator extends Authenticator {

    private final String username;
    private final String password;

    public MailSessionAuthenticator(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(username, password);
    }
}
```

## Annotation Handlers

### @MailSessionDefinition Handler

```java
public class MailSessionDefinitionHandler {

    public void processMailSessionDefinition(MailSessionDefinition annotation,
                                            String application, String module) {
        // Create MailResource from annotation
        MailResourceDescriptor descriptor = new MailResourceDescriptor();

        descriptor.setJndiName(annotation.name());
        descriptor.setHost(annotation.host());
        descriptor.setUser(annotation.user());
        descriptor.setPassword(annotation.password());
        descriptor.setFrom(annotation.from());
        descriptor.setStoreProtocol(annotation.storeProtocol());
        descriptor.setTransportProtocol(annotation.transportProtocol());

        // Process properties
        for (MailSessionProperty property : annotation.properties()) {
            descriptor.addProperty(property.name(), property.value());
        }

        // Deploy the resource
        deployMailResource(descriptor, application, module);
    }
}
```

### @MailSessionDefinitions Handler

```java
public class MailSessionDefinitionsHandler {

    public void processMailSessionDefinitions(MailSessionDefinitions annotation,
                                             String application, String module) {
        for (MailSessionDefinition definition : annotation.value()) {
            // Process each @MailSessionDefinition
            processMailSessionDefinition(definition, application, module);
        }
    }
}
```

## MailBean

```java
public class MailBean extends JavaEEResourceBase {

    private String storeProtocol;
    private String storeProtocolClass;
    private String transportProtocol;
    private String transportProtocolClass;
    private String mailHost;
    private String username;
    private String password;
    private String mailFrom;
    private boolean auth;
    private boolean debug;

    @Override
    public int getType() {
        return JavaEEResource.MAIL_RESOURCE;
    }

    // Getters and setters...
}
```

## MailConfiguration

```java
public class MailConfiguration implements Serializable {

    private final MailBean mailBean;

    public MailConfiguration(MailBean mailBean) {
        this.mailBean = mailBean;
    }

    public String getStoreProtocol() {
        return mailBean.getStoreProtocol();
    }

    public String getTransportProtocol() {
        return mailBean.getTransportProtocol();
    }

    public String getMailHost() {
        return mailBean.getMailHost();
    }

    public String getUsername() {
        return mailBean.getUsername();
    }

    public String getPassword() {
        return mailBean.getPassword();
    }

    public boolean isAuth() {
        return mailBean.isAuth();
    }

    public boolean isDebug() {
        return mailBean.isDebug();
    }

    public List<ResourceProperty> getProperties() {
        return mailBean.getProperties();
    }
}
```

## Usage Examples

### Using @MailSessionDefinition

```java
@MailSessionDefinition(
    name = "java:app/mail/Support",
    host = "smtp.example.com",
    user = "support@example.com",
    from = "support@example.com",
    transportProtocol = "smtp",
    properties = {
        @Property(name = "mail.smtp.auth", value = "true"),
        @Property(name = "mail.smtp.starttls.enable", value = "true"),
        @Property(name = "mail.smtp.port", value = "587")
    }
)
@ApplicationScoped
public class CustomerSupportService {

    @Inject
    @MailSessionSupport
    private Session mailSession;

    public void sendSupportTicket(String customerEmail, String ticketContent) {
        // Use mailSession to send email
    }
}
```

### Using JNDI Lookup

```java
public class EmailService {

    @Resource(lookup = "mail/MyMailSession")
    private Session mailSession;

    public void sendEmail(String to, String subject, String body) throws MessagingException {
        Message message = new MimeMessage(mailSession);
        message.setFrom(new InternetAddress("noreply@example.com"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setText(body);

        Transport.send(message);
    }
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.mail-api` | Jakarta Mail API |
| `angus-mail` | Jakarta Mail implementation |
| `resources-connector` | Resource APIs (JavaEEResource, ResourceInfo) |
| `resourcebase/resources` | ResourceDeployer, ResourceNamingService |

## Notes

- **Jakarta Mail** - Uses Jakarta Mail 2.x (Angus Mail)
- **JNDI Binding** - Mail sessions published to JNDI
- **Annotation Support** - @MailSessionDefinition/@MailSessionDefinitions
- **Authentication** - Supports SMTP authentication via Authenticator
- **Debug Mode** - Configurable for troubleshooting
- **Custom Properties** - Additional mail properties supported
- **Translation** - Supports ${...} system property expansion
