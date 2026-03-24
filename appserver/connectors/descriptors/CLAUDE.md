# CLAUDE.md - Connectors Descriptors

This file provides guidance for working with the `descriptors` module - JCA deployment descriptors.

## Module Overview

The descriptors module provides XML schemas and DTDs for validating JCA deployment descriptors (ra.xml).

## Build Commands

```bash
# Build descriptors
mvn -DskipTests clean package -f appserver/connectors/descriptors/pom.xml
```

## Module Type

- **Packaging**: `distribution-fragment`
- **Purpose**: Deployment descriptor validation

## Descriptor Files

Located in `src/main/resources/`:

| File | Version | Purpose |
|------|---------|---------|
| `connector_1_5.xsd` | JCA 1.5 | Schema validation |
| `connector_1_6.xsd` | JCA 1.6 | Schema validation |
| `connector_1_7.xsd` | JCA 1.7 | Schema validation |
| `sun-connector_1_4-0.dtd` | Sun DTD | Legacy support |

## ra.xml Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<connector xmlns="http://xmlns.jcp.org/xml/ns/javaee"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee
           http://xmlns.jcp.org/xml/ns/javaee/connector_1_7.xsd"
           version="1.7">
    <description>My Resource Adapter</description>
    <vendor-name>My Vendor</vendor-name>
    <eis-type>My EIS</eis-type>
    <resourceadapter-version>1.0</resourceadapter-version>

    <resourceadapter>
        <resourceadapter-class>com.example.MyResourceAdapter</resourceadapter-class>

        <config-property>
            <config-property-name>serverName</config-property-name>
            <config-property-type>java.lang.String</config-property-type>
            <config-property-value>localhost</config-property-value>
        </config-property>

        <outbound-resourceadapter>
            <connection-definition>
                <managedconnectionfactory-class>
                    com.example.MyManagedConnectionFactory
                </managedconnectionfactory-class>
                <connectionfactory-interface>
                    com.example.MyConnectionFactory
                </connectionfactory-interface>
                <connectionfactory-impl-class>
                    com.example.MyConnectionFactoryImpl
                </connectionfactory-impl-class>
                <connection-interface>
                    com.example.MyConnection
                </connection-interface>
                <connection-impl-class>
                    com.example.MyConnectionImpl
                </connection-impl-class>

                <config-property>
                    <config-property-name>connectionURL</config-property-name>
                    <config-property-type>java.lang.String</config-property-type>
                </config-property>
            </connection-definition>

            <transaction-support>XATransaction</transaction-support>
            <authentication-mechanism>
                <authentication-mechanism-type>
                    BasicPassword
                </authentication-mechanism-type>
                <credential-interface>
                    jakarta.resource.spi.security.PasswordCredential
                </credential-interface>
            </authentication-mechanism>

            <reauthentication-support>false</reauthentication-support>
        </outbound-resourceadapter>

        <inbound-resourceadapter>
            <messageadapter>
                <messagelistener>
                    <messagelistener-type>
                        jakarta.jms.MessageListener
                    </messagelistener-type>
                </messagelistener>
            </messageadapter>
        </inbound-resourceadapter>

        <adminobject>
            <adminobject-interface>com.example.MyAdminObject</adminobject-interface>
            <adminobject-class>com.example.MyAdminObjectImpl</adminobject-class>
            <config-property>
                <config-property-name>name</config-property-name>
                <config-property-type>java.lang.String</config-property-type>
            </config-property>
        </adminobject>

        <security-permission>
            <description>Permission to connect</description>
            <security-permission-spec>
                java.net.SocketPermission "localhost:1527", "connect"
            </security-permission-spec>
        </security-permission>
    </resourceadapter>
</connector>
```

## Package Structure

```
descriptors.jar (produced)
├── connector_1_5.xsd
├── connector_1_6.xsd
└── connector_1_7.xsd
```

## Related Modules

- `connectors-runtime` - Uses descriptors for RAR parsing
- `deployment-common` - Descriptor validation infrastructure
