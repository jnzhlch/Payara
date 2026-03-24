# CLAUDE.md - JavaMail Resources

This file provides guidance for working with the `appserver/resources/javamail` module - JavaMail session resources for Payara Server.

## Module Overview

The javamail module provides JavaMail session resource support for Payara Server. It includes admin CLI commands, configuration beans, runtime deployer, and annotation support for defining mail resources.

**Key Components:**
- **Admin CLI** - Commands for creating/deleting/listing mail resources
- **Runtime Deployer** - Deploys mail sessions to JNDI
- **Annotation Support** - @MailSessionDefinition/@MailSessionDefinitions

## Build Commands

```bash
# Build javamail module
mvn -DskipTests clean package -f appserver/resources/javamail/pom.xml

# Build specific submodule
mvn -DskipTests clean package -f appserver/resources/javamail/javamail-runtime/pom.xml
mvn -DskipTests clean package -f appserver/resources/javamail/javamail-connector/pom.xml
```

## Submodules

```
javamail/
├── javamail-connector/        # Admin CLI and config
├── javamail-runtime/          # Runtime deployer and annotation support
└── javamail-connector-l10n/   # Localization
```

## Admin CLI Commands

### Create JavaMail Resource

```bash
asadmin create-javamail-resource \
    --mailhost smtp.example.com \
    --mailuser user@example.com \
    --from postmaster@example.com \
    --transportProtocol smtp \
    --storeProtocol imap3 \
    --smtpHost smtp.example.com \
    --smtpPort 25 \
    --imapHost imap.example.com \
    --imapPort 143 \
    --debug=true \
    --enabled=true \
    --description "My Mail Session" \
    --property mail.smtp.auth=true:mail.smtp.starttls.enable=true \
    mail/MyMailSession
```

### Delete JavaMail Resource

```bash
asadmin delete-javamail-resource mail/MyMailSession
```

### List JavaMail Resources

```bash
asadmin list-javamail-resources
```

## MailResourceDeployer

### Deployment Process

```java
@Service
@ResourceDeployerInfo(MailResource.class)
@Singleton
public class MailResourceDeployer extends GlobalResourceDeployer
        implements ResourceDeployer {

    public void installMailResource(MailBean mailResource, ResourceInfo resourceInfo) {
        // Create MailConfiguration
        MailConfiguration config = new MailConfiguration(mailResource);

        // Create JNDI Reference with Session class and factory
        Reference ref = new Reference(jakarta.mail.Session.class.getName(),
                                     MailNamingObjectFactory.class.getName(), null);

        // Add serializable configuration
        ref.add(new SerializableObjectRefAddr("jndiName", config));

        // Publish to JNDI
        namingService.publishObject(resourceInfo, ref, true);
    }
}
```

### Mail Bean Configuration

```java
public static MailBean toMailBean(MailResource mailResourceConfig, ResourceInfo resourceInfo) {
    MailBean mailResource = new MailBean(resourceInfo);

    // Basic configuration
    mailResource.setStoreProtocol(mailResourceConfig.getStoreProtocol());
    mailResource.setTransportProtocol(mailResourceConfig.getTransportProtocol());
    mailResource.setMailHost(mailResourceConfig.getHost());
    mailResource.setUsername(mailResourceConfig.getUser());
    mailResource.setPassword(mailResourceConfig.getPassword());

    // Authentication and sender
    mailResource.setAuth(Boolean.valueOf(mailResourceConfig.getAuth()));
    mailResource.setMailFrom(mailResourceConfig.getFrom());

    // Debug mode
    mailResource.setDebug(Boolean.valueOf(mailResourceConfig.getDebug()));

    // Custom properties
    for (Property property : mailResourceConfig.getProperty()) {
        ResourceProperty rp = new ResourcePropertyImpl(property.getName(), property.getValue());
        mailResource.addProperty(rp);
    }

    return mailResource;
}
```

## MailNamingObjectFactory

### JNDI ObjectFactory for Mail Sessions

```java
public class MailNamingObjectFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                   Hashtable<?, ?> environment) throws Exception {
        // Extract MailConfiguration from Reference
        Reference ref = (Reference) obj;
        SerializableObjectRefAddr sor = (SerializableObjectRefAddr) ref.get(0);
        MailConfiguration config = (MailConfiguration) sor.getContent();

        // Create Jakarta Mail Session
        Session session = createMailSession(config);

        return session;
    }

    private Session createMailSession(MailConfiguration config) {
        Properties props = new Properties();

        // Set mail protocols
        props.put("mail.store.protocol", config.getStoreProtocol());
        props.put("mail.transport.protocol", config.getTransportProtocol());

        // Set mail host
        props.put("mail.host", config.getMailHost());
        props.put("mail.smtp.host", config.getMailHost());
        props.put("mail.imap.host", config.getMailHost());

        // Set authentication
        if (config.isAuth()) {
            props.put("mail.smtp.auth", "true");
            Authenticator auth = new MailSessionAuthenticator(config.getUsername(), config.getPassword());
            return Session.getInstance(props, auth);
        }

        return Session.getInstance(props);
    }
}
```

## Annotation Support

### @MailSessionDefinition

```java
@MailSessionDefinition(
    name = "java:mail/MyMailSession",
    host = "smtp.example.com",
    user = "user@example.com",
    password = "secret",
    from = "noreply@example.com",
    storeProtocol = "imap",
    transportProtocol = "smtp",
    properties = {
        @Property(name = "mail.smtp.auth", value = "true"),
        @Property(name = "mail.smtp.starttls.enable", value = "true")
    }
)
public class MyService {

    @Resource(lookup = "java:mail/MyMailSession")
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

### @MailSessionDefinitions (Multiple)

```java
@MailSessionDefinitions({
    @MailSessionDefinition(
        name = "java:mail/SMTP",
        host = "smtp.example.com",
        transportProtocol = "smtp"
    ),
    @MailSessionDefinition(
        name = "java:mail/IMAP",
        host = "imap.example.com",
        storeProtocol = "imap"
    )
})
public class MailServices {
    // ...
}
```

## Mail Configuration in XML

### glassfish-resources.xml / payara-resources.xml

```xml
<resources>
    <mail-resource
        jndi-name="mail/MyMailSession"
        host="smtp.example.com"
        user="user@example.com"
        password="secret"
        from="noreply@example.com"
        store-protocol="imap"
        transport-protocol="smtp"
        debug="false"
        enabled="true">
        <property name="mail.smtp.auth" value="true"/>
        <property name="mail.smtp.starttls.enable" value="true"/>
        <property name="mail.smtp.port" value="587"/>
    </mail-resource>
</resources>
```

### domain.xml Configuration

```xml
<resources>
    <mail-resource
        jndi-name="mail/MyMailSession"
        host="smtp.example.com"
        user="user@example.com"
        from="noreply@example.com"
        store-protocol="imap"
        transport-protocol="smtp"
        object-type="user"
        enabled="true">
        <property name="mail.smtp.auth" value="true"/>
        <property name="mail.smtp.starttls.enable" value="true"/>
    </mail-resource>
</resources>
```

## Common Mail Properties

### SMTP Properties

```properties
# Basic SMTP
mail.smtp.host=smtp.example.com
mail.smtp.port=25
mail.smtp.auth=true
mail.smtp.starttls.enable=true
mail.smtp.ssl.enable=true
mail.smtp.ssl.trust=*

# Timeout settings
mail.smtp.connectiontimeout=5000
mail.smtp.timeout=5000
mail.smtp.writetimeout=5000

# Submission (port 587)
mail.smtp.submitter=true
mail.smtp.ehlo=true
```

### IMAP Properties

```properties
# Basic IMAP
mail.imap.host=imap.example.com
mail.imap.port=143
mail.imap.ssl.enable=true
mail.imap.connectiontimeout=5000

# IMAP IDLE
mail.imap.idle=true
```

## Mail Session Usage

### Sending Email

```java
@Resource(lookup = "mail/MyMailSession")
private Session mailSession;

public void sendEmail(String to, String subject, String body) throws MessagingException {
    Message message = new MimeMessage(mailSession);
    message.setFrom(new InternetAddress("from@example.com"));
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
    message.setSubject(subject);
    message.setSentDate(new Date());

    // Text content
    message.setText(body);

    // HTML content
    //message.setContent(body, "text/html");

    Transport.send(message);
}
```

### Receiving Email (IMAP)

```java
@Resource(lookup = "mail/MyMailSession")
private Session mailSession;

public void readEmail() throws MessagingException, IOException {
    Store store = mailSession.getStore("imap");
    store.connect("username", "password");

    Folder inbox = store.getFolder("INBOX");
    inbox.open(Folder.READ_ONLY);

    Message[] messages = inbox.getMessages();
    for (Message message : messages) {
        System.out.println("Subject: " + message.getSubject());
        System.out.println("From: " + message.getFrom()[0]);
        System.out.println("Body: " + message.getContent());
    }

    inbox.close(false);
    store.close();
}
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.mail-api` | Jakarta Mail API |
| `angus-mail` | Jakarta Mail implementation |
| `resources-connector` | Resource APIs and configuration |
| `resourcebase/resources` | ResourceDeployer interface |

## Notes

- **Jakarta Mail** - Uses Jakarta Mail 2.x / Angus Mail
- **JNDI Binding** - Mail sessions bound to JNDI namespace
- **Authentication** - Supports SMTP authentication
- **Debug Mode** - Configurable debug logging for troubleshooting
- **Custom Properties** - Additional mail properties can be configured
- **Resource Preservation** - App-scoped mail resources preserved across redeployments
