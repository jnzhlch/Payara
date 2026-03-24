# CLAUDE.md - JavaMail Connector

This file provides guidance for working with the `appserver/resources/javamail/javamail-connector` module - Admin CLI and configuration beans for JavaMail resources.

## Module Overview

The javamail-connector module provides admin CLI commands and configuration beans for managing JavaMail session resources in Payara Server.

**Key Components:**
- **Admin CLI** - Commands for creating/deleting/listing mail resources
- **Configuration Beans** - MailResource config bean
- **ResourceManager** - Resource manager implementation for mail resources

## Build Commands

```bash
# Build javamail-connector module
mvn -DskipTests clean package -f appserver/resources/javamail/javamail-connector/pom.xml
```

## Module Contents

```
javamail-connector/
└── src/main/java/org/glassfish/resources/javamail/
    ├── admin/cli/
    │   ├── CreateJavaMailResource.java
    │   ├── DeleteJavaMailResource.java
    │   ├── ListJavaMailResources.java
    │   └── JavaMailResourceManager.java
    └── config/
        └── MailResource.java
```

## Admin CLI Commands

### create-javamail-resource

```bash
asadmin create-javamail-resource \
    --mailhost smtp.example.com \
    --mailuser user@example.com \
    --from postmaster@example.com \
    --transportProtocol smtp \
    --storeProtocol imap \
    --smtpHost smtp.example.com \
    --smtpPort 25 \
    --imapHost imap.example.com \
    --imapPort 143 \
    --debug=true \
    --enabled=true \
    --description "Corporate Mail Session" \
    --property mail.smtp.auth=true:mail.smtp.starttls.enable=true \
    mail/MyMailSession
```

#### Command Options

| Option | Description | Default |
|--------|-------------|---------|
| `--mailhost` | Mail server host name | localhost |
| `--mailuser` | Username for authentication | - |
| `--from` | Email address for "From" header | - |
| `--transportProtocol` | Transport protocol (smtp) | smtp |
| `--storeProtocol` | Store protocol (imap, pop3) | imap |
| `--smtpHost` | SMTP server host | ${mailhost} |
| `--smtpPort` | SMTP server port | 25 |
| `--imapHost` | IMAP server host | ${mailhost} |
| `--imapPort` | IMAP server port | 143 |
| `--debug` | Enable debug logging | false |
| `--enabled` | Enable resource | true |
| `--description` | Resource description | - |
| `--property` | Additional mail properties | - |
| `--target` | Target (server, cluster, instance) | server |

### delete-javamail-resource

```bash
asadmin delete-javamail-resource mail/MyMailSession
```

### list-javamail-resources

```bash
asadmin list-javamail-resources
```

#### Command Options

| Option | Description |
|--------|-------------|
| `--long` | Display detailed output |
| `--target` | List resources for specific target |

## JavaMailResourceManager

```java
@Service
public class JavaMailResourceManager implements ResourceManager {

    @Override
    public Resource createConfigBean(Resources resources, HashMap attrList,
                                    Properties props, boolean validate) {
        // Create MailResource config bean
        MailResource mailResource = resources.createChild(MailResource.class);

        // Set attributes
        mailResource.setJndiName((String) attrList.get(JNDI_NAME));
        mailResource.setHost((String) attrList.get(MAIL_HOST));
        mailResource.setUser((String) attrList.get(MAIL_USER));
        mailResource.setFrom((String) attrList.get(MAIL_FROM));
        mailResource.setStoreProtocol((String) attrList.get(STORE_PROTOCOL));
        mailResource.setTransportProtocol((String) attrList.get(TRANSPORT_PROTOCOL));
        mailResource.setDebug((String) attrList.get(DEBUG));
        mailResource.setEnabled((String) attrList.get(ENABLED));

        // Add properties
        for (String key : props.stringPropertyNames()) {
            Property prop = mailResource.createChild(Property.class);
            prop.setName(key);
            prop.setValue(props.getProperty(key));
            mailResource.getProperty().add(prop);
        }

        return mailResource;
    }

    @Override
    public void deleteResource(Resource resource) throws Exception {
        MailResource mailResource = (MailResource) resource;
        // Undeploy and delete from configuration
    }
}
```

## MailResource Configuration Bean

```java
public interface MailResource extends ConfigBean, Resource {

    String getJndiName();
    void setJndiName(String value);

    String getHost();
    void setHost(String value);

    String getUser();
    void setUser(String value);

    String getPassword();
    void setPassword(String value);

    String getFrom();
    void setFrom(String value);

    String getStoreProtocol();
    void setStoreProtocol(String value);

    String getStoreProtocolClass();
    void setStoreProtocolClass(String value);

    String getTransportProtocol();
    void setTransportProtocol(String value);

    String getTransportProtocolClass();
    void setTransportProtocolClass(String value);

    String getDebug();
    void setDebug(String value);

    String getAuth();
    void setAuth(String value);

    List<Property> getProperty();
}
```

## domain.xml Configuration

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
        enabled="true"
        object-type="user">
        <property name="mail.smtp.auth" value="true"/>
        <property name="mail.smtp.starttls.enable" value="true"/>
        <property name="mail.smtp.port" value="587"/>
    </mail-resource>
</resources>
```

## glassfish-resources.xml Configuration

```xml
<resources>
    <mail-resource
        jndi-name="mail/MyMailSession"
        host="smtp.example.com"
        user="user@example.com"
        from="noreply@example.com"
        store-protocol="imap"
        transport-protocol="smtp">
        <property name="mail.smtp.auth" value="true"/>
        <property name="mail.smtp.starttls.enable" value="true"/>
    </mail-resource>
</resources>
```

## Common Mail Properties

### SMTP Properties

```xml
<property name="mail.smtp.host" value="smtp.example.com"/>
<property name="mail.smtp.port" value="25"/>
<property name="mail.smtp.auth" value="true"/>
<property name="mail.smtp.starttls.enable" value="true"/>
<property name="mail.smtp.ssl.enable" value="true"/>
<property name="mail.smtp.ssl.trust" value="*"/>
<property name="mail.smtp.connectiontimeout" value="5000"/>
<property name="mail.smtp.timeout" value="5000"/>
<property name="mail.smtp.writetimeout" value="5000"/>
```

### IMAP Properties

```xml
<property name="mail.imap.host" value="imap.example.com"/>
<property name="mail.imap.port" value="143"/>
<property name="mail.imap.ssl.enable" value="true"/>
<property name="mail.imap.connectiontimeout" value="5000"/>
<property name="mail.imap.idle" value="true"/>
```

### POP3 Properties

```xml
<property name="mail.pop3.host" value="pop.example.com"/>
<property name="mail.pop3.port" value="110"/>
<property name="mail.pop3.ssl.enable" value="true"/>
```

## Module Dependencies

| Dependency | Purpose |
|------------|---------|
| `jakarta.mail-api` | Jakarta Mail API |
| `config-api` | Configuration beans (ConfigBean, Property) |
| `resources-connector` | ResourceManager interface |

## Notes

- **Jakarta Mail** - Uses Jakarta Mail 2.x API
- **Global Resources** - Managed at domain level
- **App-Scoped** - Can be defined in payara-resources.xml
- **Password Storage** - Encrypted in domain.xml
- **Debug Mode** - Useful for troubleshooting mail issues
- **Properties** - Custom properties override defaults
