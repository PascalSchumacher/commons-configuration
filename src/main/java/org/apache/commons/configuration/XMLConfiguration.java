/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.configuration.convert.ListDelimiterHandler;
import org.apache.commons.configuration.ex.ConfigurationException;
import org.apache.commons.configuration.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration.io.FileLocator;
import org.apache.commons.configuration.io.FileLocatorAware;
import org.apache.commons.configuration.io.InputStreamSupport;
import org.apache.commons.configuration.resolver.DefaultEntityResolver;
import org.apache.commons.configuration.resolver.EntityRegistry;
import org.apache.commons.configuration.tree.ConfigurationNode;
import org.apache.commons.configuration.tree.DefaultConfigurationNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * <p>A specialized hierarchical configuration class that is able to parse XML
 * documents.</p>
 *
 * <p>The parsed document will be stored keeping its structure. The class also
 * tries to preserve as much information from the loaded XML document as
 * possible, including comments and processing instructions. These will be
 * contained in documents created by the {@code save()} methods, too.</p>
 *
 * <p>Like other file based configuration classes this class maintains the name
 * and path to the loaded configuration file. These properties can be altered
 * using several setter methods, but they are not modified by {@code save()}
 * and {@code load()} methods. If XML documents contain relative paths to
 * other documents (e.g. to a DTD), these references are resolved based on the
 * path set for this configuration.</p>
 *
 * <p>By inheriting from {@link AbstractConfiguration} this class
 * provides some extended functionality, e.g. interpolation of property values.
 * Like in {@link PropertiesConfiguration} property values can
 * contain delimiter characters (the comma ',' per default) and are then split
 * into multiple values. This works for XML attributes and text content of
 * elements as well. The delimiter can be escaped by a backslash. As an example
 * consider the following XML fragment:</p>
 *
 * <p>
 * <pre>
 * &lt;config&gt;
 *   &lt;array&gt;10,20,30,40&lt;/array&gt;
 *   &lt;scalar&gt;3\,1415&lt;/scalar&gt;
 *   &lt;cite text="To be or not to be\, this is the question!"/&gt;
 * &lt;/config&gt;
 * </pre>
 * </p>
 * <p>Here the content of the {@code array} element will be split at
 * the commas, so the {@code array} key will be assigned 4 values. In the
 * {@code scalar} property and the {@code text} attribute of the
 * {@code cite} element the comma is escaped, so that no splitting is
 * performed.</p>
 *
 * <p>The configuration API allows setting multiple values for a single attribute,
 * e.g. something like the following is legal (assuming that the default
 * expression engine is used):
 * <pre>
 * XMLConfiguration config = new XMLConfiguration();
 * config.addProperty("test.dir[@name]", "C:\\Temp\\");
 * config.addProperty("test.dir[@name]", "D:\\Data\\");
 * </pre>
 * However, in XML such a constellation is not supported; an attribute
 * can appear only once for a single element. Therefore, an attempt to save
 * a configuration which violates this condition will throw an exception.</p>
 *
 * <p>Like other {@code Configuration} implementations, {@code XMLConfiguration}
 * uses a {@link ListDelimiterHandler} object for controlling list split
 * operations. Per default, a list delimiter handler object is set which
 * disables this feature. XML has a built-in support for complex structures
 * including list properties; therefore, list splitting is not that relevant
 * for this configuration type. Nevertheless, by setting an alternative
 * {@code ListDelimiterHandler} implementation, this feature can be enabled.
 * It works as for any other concrete {@code Configuration} implementation.</p>
 *
 * <p>Whitespace in the content of XML documents is trimmed per default. In most
 * cases this is desired. However, sometimes whitespace is indeed important and
 * should be treated as part of the value of a property as in the following
 * example:
 * <pre>
 *   &lt;indent&gt;    &lt;/indent&gt;
 * </pre></p>
 *
 * <p>Per default the spaces in the {@code indent} element will be trimmed
 * resulting in an empty element. To tell {@code XMLConfiguration} that
 * spaces are relevant the {@code xml:space} attribute can be used, which is
 * defined in the <a href="http://www.w3.org/TR/REC-xml/#sec-white-space">XML
 * specification</a>. This will look as follows:
 * <pre>
 *   &lt;indent <strong>xml:space=&quot;preserve&quot;</strong>&gt;    &lt;/indent&gt;
 * </pre>
 * The value of the {@code indent} property will now contain the spaces.</p>
 *
 * <p>{@code XMLConfiguration} implements the {@link FileConfiguration}
 * interface and thus provides full support for loading XML documents from
 * different sources like files, URLs, or streams. A full description of these
 * features can be found in the documentation of
 * {@link AbstractFileConfiguration}.</p>
 *
 * <p>Like other {@code Configuration} implementations, this class uses a
 * {@code Synchronizer} object to control concurrent access. By choosing a
 * suitable implementation of the {@code Synchronizer} interface, an instance
 * can be made thread-safe or not. Note that access to most of the properties
 * typically set through a builder is not protected by the {@code Synchronizer}.
 * The intended usage is that these properties are set once at construction
 * time through the builder and after that remain constant. If you wish to
 * change such properties during life time of an instance, you have to use
 * the {@code lock()} and {@code unlock()} methods manually to ensure that
 * other threads see your changes.
 * </p>
 *
 * @since commons-configuration 1.0
 *
 * @author J&ouml;rg Schaible
 * @version $Id$
 */
public class XMLConfiguration extends BaseHierarchicalConfiguration implements
        EntityResolver, EntityRegistry, FileBasedConfiguration,
        FileLocatorAware, InputStreamSupport
{
    /**
     * The serial version UID.
     */
    private static final long serialVersionUID = 2453781111653383552L;

    /** Constant for the default root element name. */
    private static final String DEFAULT_ROOT_NAME = "configuration";

    /** Constant for the name of the space attribute.*/
    private static final String ATTR_SPACE = "xml:space";

    /** Constant for the xml:space value for preserving whitespace.*/
    private static final String VALUE_PRESERVE = "preserve";

    /** Schema Langauge key for the parser */
    private static final String JAXP_SCHEMA_LANGUAGE =
        "http://java.sun.com/xml/jaxp/properties/schemaLanguage";

    /** Schema Language for the parser */
    private static final String W3C_XML_SCHEMA =
        "http://www.w3.org/2001/XMLSchema";

    /** The document from this configuration's data source. */
    private Document document;

    /** Stores the name of the root element. */
    private String rootElementName;

    /** Stores the public ID from the DOCTYPE.*/
    private String publicID;

    /** Stores the system ID from the DOCTYPE.*/
    private String systemID;

    /** Stores the document builder that should be used for loading.*/
    private DocumentBuilder documentBuilder;

    /** Stores a flag whether DTD or Schema validation should be performed.*/
    private boolean validating;

    /** Stores a flag whether DTD or Schema validation is used */
    private boolean schemaValidation;

    /** The EntityResolver to use */
    private EntityResolver entityResolver = new DefaultEntityResolver();

    /** The current file locator. */
    private FileLocator locator;

    /**
     * Creates a new instance of {@code XMLConfiguration}.
     */
    public XMLConfiguration()
    {
        super();
        setLogger(LogFactory.getLog(XMLConfiguration.class));
    }

    /**
     * Creates a new instance of {@code XMLConfiguration} and copies the
     * content of the passed in configuration into this object. Note that only
     * the data of the passed in configuration will be copied. If, for instance,
     * the other configuration is a {@code XMLConfiguration}, too,
     * things like comments or processing instructions will be lost.
     *
     * @param c the configuration to copy
     * @since 1.4
     */
    public XMLConfiguration(HierarchicalConfiguration c)
    {
        super(c);
        clearReferences(getRootNode());
        setRootElementName(getRootNode().getName());
        setLogger(LogFactory.getLog(XMLConfiguration.class));
    }

    /**
     * Returns the name of the root element. If this configuration was loaded
     * from a XML document, the name of this document's root element is
     * returned. Otherwise it is possible to set a name for the root element
     * that will be used when this configuration is stored.
     *
     * @return the name of the root element
     */
    @Override
    protected String getRootElementNameInternal()
    {
        if (document == null)
        {
            return (rootElementName == null) ? DEFAULT_ROOT_NAME : rootElementName;
        }
        else
        {
            return document.getDocumentElement().getNodeName();
        }
    }

    /**
     * Sets the name of the root element. This name is used when this
     * configuration object is stored in an XML file. Note that setting the name
     * of the root element works only if this configuration has been newly
     * created. If the configuration was loaded from an XML file, the name
     * cannot be changed and an {@code UnsupportedOperationException}
     * exception is thrown. Whether this configuration has been loaded from an
     * XML document or not can be found out using the {@code getDocument()}
     * method.
     *
     * @param name the name of the root element
     */
    public void setRootElementName(String name)
    {
        if (getDocument() != null)
        {
            throw new UnsupportedOperationException("The name of the root element "
                    + "cannot be changed when loaded from an XML document!");
        }
        rootElementName = name;
        getRootNode().setName(name);
    }

    /**
     * Returns the {@code DocumentBuilder} object that is used for
     * loading documents. If no specific builder has been set, this method
     * returns <b>null</b>.
     *
     * @return the {@code DocumentBuilder} for loading new documents
     * @since 1.2
     */
    public DocumentBuilder getDocumentBuilder()
    {
        return documentBuilder;
    }

    /**
     * Sets the {@code DocumentBuilder} object to be used for loading
     * documents. This method makes it possible to specify the exact document
     * builder. So an application can create a builder, configure it for its
     * special needs, and then pass it to this method.
     *
     * @param documentBuilder the document builder to be used; if undefined, a
     * default builder will be used
     * @since 1.2
     */
    public void setDocumentBuilder(DocumentBuilder documentBuilder)
    {
        this.documentBuilder = documentBuilder;
    }

    /**
     * Returns the public ID of the DOCTYPE declaration from the loaded XML
     * document. This is <b>null</b> if no document has been loaded yet or if
     * the document does not contain a DOCTYPE declaration with a public ID.
     *
     * @return the public ID
     * @since 1.3
     */
    public String getPublicID()
    {
        beginRead(false);
        try
        {
            return publicID;
        }
        finally
        {
            endRead();
        }
    }

    /**
     * Sets the public ID of the DOCTYPE declaration. When this configuration is
     * saved, a DOCTYPE declaration will be constructed that contains this
     * public ID.
     *
     * @param publicID the public ID
     * @since 1.3
     */
    public void setPublicID(String publicID)
    {
        beginWrite(false);
        try
        {
            this.publicID = publicID;
        }
        finally
        {
            endWrite();
        }
    }

    /**
     * Returns the system ID of the DOCTYPE declaration from the loaded XML
     * document. This is <b>null</b> if no document has been loaded yet or if
     * the document does not contain a DOCTYPE declaration with a system ID.
     *
     * @return the system ID
     * @since 1.3
     */
    public String getSystemID()
    {
        beginRead(false);
        try
        {
            return systemID;
        }
        finally
        {
            endRead();
        }
    }

    /**
     * Sets the system ID of the DOCTYPE declaration. When this configuration is
     * saved, a DOCTYPE declaration will be constructed that contains this
     * system ID.
     *
     * @param systemID the system ID
     * @since 1.3
     */
    public void setSystemID(String systemID)
    {
        beginWrite(false);
        try
        {
            this.systemID = systemID;
        }
        finally
        {
            endWrite();
        }
    }

    /**
     * Returns the value of the validating flag.
     *
     * @return the validating flag
     * @since 1.2
     */
    public boolean isValidating()
    {
        return validating;
    }

    /**
     * Sets the value of the validating flag. This flag determines whether
     * DTD/Schema validation should be performed when loading XML documents. This
     * flag is evaluated only if no custom {@code DocumentBuilder} was set.
     *
     * @param validating the validating flag
     * @since 1.2
     */
    public void setValidating(boolean validating)
    {
        if (!schemaValidation)
        {
            this.validating = validating;
        }
    }


    /**
     * Returns the value of the schemaValidation flag.
     *
     * @return the schemaValidation flag
     * @since 1.7
     */
    public boolean isSchemaValidation()
    {
        return schemaValidation;
    }

    /**
     * Sets the value of the schemaValidation flag. This flag determines whether
     * DTD or Schema validation should be used. This
     * flag is evaluated only if no custom {@code DocumentBuilder} was set.
     * If set to true the XML document must contain a schemaLocation definition
     * that provides resolvable hints to the required schemas.
     *
     * @param schemaValidation the validating flag
     * @since 1.7
     */
    public void setSchemaValidation(boolean schemaValidation)
    {
        this.schemaValidation = schemaValidation;
        if (schemaValidation)
        {
            this.validating = true;
        }
    }

    /**
     * Sets a new EntityResolver. Setting this will cause RegisterEntityId to have no
     * effect.
     * @param resolver The EntityResolver to use.
     * @since 1.7
     */
    public void setEntityResolver(EntityResolver resolver)
    {
        this.entityResolver = resolver;
    }

    /**
     * Returns the EntityResolver.
     * @return The EntityResolver.
     * @since 1.7
     */
    public EntityResolver getEntityResolver()
    {
        return this.entityResolver;
    }

    /**
     * Returns the XML document this configuration was loaded from. The return
     * value is <b>null</b> if this configuration was not loaded from a XML
     * document.
     *
     * @return the XML document this configuration was loaded from
     */
    public Document getDocument()
    {
        beginRead(false);
        try
        {
            return document;
        }
        finally
        {
            endRead();
        }
    }

    /**
     * Removes all properties from this configuration. If this configuration
     * was loaded from a file, the associated DOM document is also cleared.
     */
    @Override
    protected void clearInternal()
    {
        super.clearInternal();
        getRootNode().setReference(null);
        document = null;
    }

    /**
     * Initializes this configuration from an XML document.
     *
     * @param document the document to be parsed
     * @param elemRefs a flag whether references to the XML elements should be set
     */
    public void initProperties(Document document, boolean elemRefs)
    {
        if (document.getDoctype() != null)
        {
            setPublicID(document.getDoctype().getPublicId());
            setSystemID(document.getDoctype().getSystemId());
        }

        constructHierarchy(getRootNode(), document.getDocumentElement(), elemRefs, true);
        getRootNode().setName(document.getDocumentElement().getNodeName());
        if (elemRefs)
        {
            getRootNode().setReference(document.getDocumentElement());
        }
    }

    /**
     * Helper method for building the internal storage hierarchy. The XML
     * elements are transformed into node objects.
     *
     * @param node the actual node
     * @param element the actual XML element
     * @param elemRefs a flag whether references to the XML elements should be
     *        set
     * @param trim a flag whether the text content of elements should be
     *        trimmed; this controls the whitespace handling
     * @return a map with all attribute values extracted for the current node;
     *         this map also contains the value of the trim flag for this node
     *         under the key {@value #ATTR_SPACE}
     */
    private Map<String, String> constructHierarchy(ConfigurationNode node,
            Element element, boolean elemRefs, boolean trim)
    {
        boolean trimFlag = shouldTrim(element, trim);
        Map<String, String> attributes =
                processAttributes(node, element, elemRefs);
        attributes.put(ATTR_SPACE, String.valueOf(trimFlag));
        StringBuilder buffer = new StringBuilder();
        NodeList list = element.getChildNodes();
        for (int i = 0; i < list.getLength(); i++)
        {
            org.w3c.dom.Node w3cNode = list.item(i);
            if (w3cNode instanceof Element)
            {
                Element child = (Element) w3cNode;
                ConfigurationNode childNode = new XMLNode(child.getTagName(),
                        elemRefs ? child : null);
                Map<String, String> attrmap =
                        constructHierarchy(childNode, child, elemRefs, trimFlag);
                node.addChild(childNode);
                Boolean childTrim = Boolean.valueOf(attrmap.remove(ATTR_SPACE));
                handleDelimiters(node, childNode, childTrim.booleanValue(), attrmap);
            }
            else if (w3cNode instanceof Text)
            {
                Text data = (Text) w3cNode;
                buffer.append(data.getData());
            }
        }

        String text = determineValue(node, buffer.toString(), trimFlag);
        if (text.length() > 0 || (!hasChildren(node) && node != getRootNode()))
        {
            node.setValue(text);
        }
        return attributes;
    }

    /**
     * Tests whether the specified node has some child elements.
     *
     * @param node the node to check
     * @return a flag whether there are child elements
     */
    private static boolean hasChildren(ConfigurationNode node)
    {
        return node.getChildrenCount() > 0 || node.getAttributeCount() > 0;
    }

    /**
     * Determines the value of a configuration node. This method mainly checks
     * whether the text value is to be trimmed or not. This is normally defined
     * by the trim flag. However, if the node has children and its content is
     * only whitespace, then it makes no sense to store any value; this would
     * only scramble layout when the configuration is saved again.
     *
     * @param node the current {@code ConfigurationNode}
     * @param content the text content of this node
     * @param trimFlag the trim flag
     * @return the value to be stored for this node
     */
    private static String determineValue(ConfigurationNode node,
            String content, boolean trimFlag)
    {
        boolean shouldTrim =
                trimFlag
                        || (StringUtils.isBlank(content) && node
                                .getChildrenCount() > 0);
        return shouldTrim ? content.trim() : content;
    }

    /**
     * Helper method for constructing node objects for the attributes of the
     * given XML element.
     *
     * @param node the current node
     * @param element the actual XML element
     * @param elemRefs a flag whether references to the XML elements should be set
     * @return a map with all attribute values extracted for the current node
     */
    private Map<String, String> processAttributes(ConfigurationNode node,
            Element element, boolean elemRefs)
    {
        NamedNodeMap attributes = element.getAttributes();
        Map<String, String> attrmap = new HashMap<String, String>();

        for (int i = 0; i < attributes.getLength(); ++i)
        {
            org.w3c.dom.Node w3cNode = attributes.item(i);
            if (w3cNode instanceof Attr)
            {
                Attr attr = (Attr) w3cNode;
                appendAttribute(node, element, elemRefs, attr.getName(), attr.getValue());
                attrmap.put(attr.getName(), attr.getValue());
            }
        }

        return attrmap;
    }

    /**
     * Adds an attribute node to the given node. Attributes are represented
     * as configuration nodes like child nodes. For each attribute value, a new
     * attribute node is created and added as child to the current node.
     *
     * @param node the current node
     * @param element the corresponding XML element
     * @param attr the name of the attribute
     * @param value the attribute value
     */
    private void appendAttribute(ConfigurationNode node, Element element, boolean elemRefs,
            String attr, String value)
    {
        ConfigurationNode child = new XMLNode(attr, elemRefs ? element : null);
        child.setValue(value);
        node.addAttribute(child);
    }

    /**
     * Deals with elements whose value is a list. In this case multiple child
     * elements must be added.
     *
     * @param parent the parent element
     * @param child the child element
     * @param trim flag whether texts of elements should be trimmed
     * @param attrmap a map with the attributes of the current node
     */
    private void handleDelimiters(ConfigurationNode parent, ConfigurationNode child, boolean trim,
            Map<String, String> attrmap)
    {
        if (child.getValue() != null)
        {
            Collection<String> values =
                    getListDelimiterHandler().split(
                            child.getValue().toString(), trim);

            if (values.size() > 1)
            {
                Iterator<String> it = values.iterator();
                // Create new node for the original child's first value
                ConfigurationNode c = createNode(child.getName());
                c.setValue(it.next());
                // Copy original attributes to the new node
                for (ConfigurationNode ndAttr : child.getAttributes())
                {
                    ndAttr.setReference(null);
                    c.addAttribute(ndAttr);
                }
                parent.removeChild(child);
                parent.addChild(c);

                // add multiple new children
                while (it.hasNext())
                {
                    c = createNode(child.getName());
                    c.setValue(it.next());
                    for (Map.Entry<String, String> e : attrmap.entrySet())
                    {
                        appendAttribute(c, null, false, e.getKey(),
                                e.getValue());
                    }
                    parent.addChild(c);
                }
            }
            else if (values.size() == 1)
            {
                // we will have to replace the value because it might
                // contain escaped delimiters
                child.setValue(values.iterator().next());
            }
        }
    }

    /**
     * Checks whether the content of the current XML element should be trimmed.
     * This method checks whether a {@code xml:space} attribute is
     * present and evaluates its value. See <a
     * href="http://www.w3.org/TR/REC-xml/#sec-white-space">
     * http://www.w3.org/TR/REC-xml/#sec-white-space</a> for more details.
     *
     * @param element the current XML element
     * @param currentTrim the current trim flag
     * @return a flag whether the content of this element should be trimmed
     */
    private boolean shouldTrim(Element element, boolean currentTrim)
    {
        Attr attr = element.getAttributeNode(ATTR_SPACE);

        if (attr == null)
        {
            return currentTrim;
        }
        else
        {
            return !VALUE_PRESERVE.equals(attr.getValue());
        }
    }

    /**
     * Creates the {@code DocumentBuilder} to be used for loading files.
     * This implementation checks whether a specific
     * {@code DocumentBuilder} has been set. If this is the case, this
     * one is used. Otherwise a default builder is created. Depending on the
     * value of the validating flag this builder will be a validating or a non
     * validating {@code DocumentBuilder}.
     *
     * @return the {@code DocumentBuilder} for loading configuration
     * files
     * @throws ParserConfigurationException if an error occurs
     * @since 1.2
     */
    protected DocumentBuilder createDocumentBuilder()
            throws ParserConfigurationException
    {
        if (getDocumentBuilder() != null)
        {
            return getDocumentBuilder();
        }
        else
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            if (isValidating())
            {
                factory.setValidating(true);
                if (isSchemaValidation())
                {
                    factory.setNamespaceAware(true);
                    factory.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
                }
            }

            DocumentBuilder result = factory.newDocumentBuilder();
            result.setEntityResolver(this.entityResolver);

            if (isValidating())
            {
                // register an error handler which detects validation errors
                result.setErrorHandler(new DefaultHandler()
                {
                    @Override
                    public void error(SAXParseException ex) throws SAXException
                    {
                        throw ex;
                    }
                });
            }
            return result;
        }
    }

    /**
     * Creates a DOM document from the internal tree of configuration nodes.
     *
     * @return the new document
     * @throws ConfigurationException if an error occurs
     */
    protected Document createDocument() throws ConfigurationException
    {
        try
        {
            if (document == null)
            {
                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document newDocument = builder.newDocument();
                Element rootElem = newDocument.createElement(getRootElementName());
                newDocument.appendChild(rootElem);
                document = newDocument;
            }

            XMLBuilderVisitor builder = new XMLBuilderVisitor(document, getListDelimiterHandler());
            builder.processDocument(getRootNode());
            initRootElementText(document, getRootNode().getValue());
            return document;
        }
        catch (DOMException domEx)
        {
            throw new ConfigurationException(domEx);
        }
        catch (ParserConfigurationException pex)
        {
            throw new ConfigurationException(pex);
        }
    }

    /**
     * Sets the text of the root element of a newly created XML Document.
     *
     * @param doc the document
     * @param value the new text to be set
     */
    private void initRootElementText(Document doc, Object value)
    {
        Element elem = doc.getDocumentElement();
        NodeList children = elem.getChildNodes();

        // Remove all existing text nodes
        for (int i = 0; i < children.getLength(); i++)
        {
            org.w3c.dom.Node nd = children.item(i);
            if (nd.getNodeType() == org.w3c.dom.Node.TEXT_NODE)
            {
                elem.removeChild(nd);
            }
        }

        if (value != null)
        {
            // Add a new text node
            elem.appendChild(doc.createTextNode(String.valueOf(value)));
        }
    }

    /**
     * Creates a new node object. This implementation returns an instance of the
     * {@code XMLNode} class.
     *
     * @param name the node's name
     * @return the new node
     */
    @Override
    protected ConfigurationNode createNode(String name)
    {
        return new XMLNode(name, null);
    }

    /**
     * {@inheritDoc} Stores the passed in locator for the upcoming IO operation.
     */
    @Override
    public void initFileLocator(FileLocator loc)
    {
        locator = loc;
    }

    /**
     * Loads the configuration from the given reader.
     * Note that the {@code clear()} method is not called, so
     * the properties contained in the loaded file will be added to the
     * current set of properties.
     *
     * @param in the reader
     * @throws ConfigurationException if an error occurs
     * @throws IOException if an IO error occurs
     */
    @Override
    public void read(Reader in) throws ConfigurationException, IOException
    {
        load(new InputSource(in));
    }

    /**
     * Loads the configuration from the given input stream. This is analogous to
     * {@link #read(Reader)}, but data is read from a stream. Note that this
     * method will be called most time when reading an XML configuration source.
     * By reading XML documents directly from an input stream, the file's
     * encoding can be correctly dealt with.
     *
     * @param in the input stream
     * @throws ConfigurationException if an error occurs
     * @throws IOException if an IO error occurs
     */
    @Override
    public void read(InputStream in) throws ConfigurationException, IOException
    {
        load(new InputSource(in));
    }

    /**
     * Loads a configuration file from the specified input source.
     *
     * @param source the input source
     * @throws ConfigurationException if an error occurs
     */
    private void load(InputSource source) throws ConfigurationException
    {
        try
        {
            URL sourceURL = locator.getSourceURL();
            if (sourceURL != null)
            {
                source.setSystemId(sourceURL.toString());
            }

            DocumentBuilder builder = createDocumentBuilder();
            Document newDocument = builder.parse(source);
            Document oldDocument = document;
            document = null;
            initProperties(newDocument, oldDocument == null);
            document = (oldDocument == null) ? newDocument : oldDocument;
        }
        catch (SAXParseException spe)
        {
            throw new ConfigurationException("Error parsing " + source.getSystemId(), spe);
        }
        catch (Exception e)
        {
            this.getLogger().debug("Unable to load the configuraton", e);
            throw new ConfigurationException("Unable to load the configuration", e);
        }
    }

    /**
     * Saves the configuration to the specified writer.
     *
     * @param writer the writer used to save the configuration
     * @throws ConfigurationException if an error occurs
     * @throws IOException if an IO error occurs
     */
    @Override
    public void write(Writer writer) throws ConfigurationException, IOException
    {
        try
        {
            Transformer transformer = createTransformer();
            Source source = new DOMSource(createDocument());
            Result result = new StreamResult(writer);
            transformer.transform(source, result);
        }
        catch (TransformerException e)
        {
            throw new ConfigurationException("Unable to save the configuration", e);
        }
        catch (TransformerFactoryConfigurationError e)
        {
            throw new ConfigurationException("Unable to save the configuration", e);
        }
    }

    /**
     * Validate the document against the Schema.
     * @throws ConfigurationException if the validation fails.
     */
    public void validate() throws ConfigurationException
    {
        beginWrite(false);
        try
        {
            Transformer transformer = createTransformer();
            Source source = new DOMSource(createDocument());
            StringWriter writer = new StringWriter();
            Result result = new StreamResult(writer);
            transformer.transform(source, result);
            Reader reader = new StringReader(writer.getBuffer().toString());
            DocumentBuilder builder = createDocumentBuilder();
            builder.parse(new InputSource(reader));
        }
        catch (SAXException e)
        {
            throw new ConfigurationException("Validation failed", e);
        }
        catch (IOException e)
        {
            throw new ConfigurationException("Validation failed", e);
        }
        catch (TransformerException e)
        {
            throw new ConfigurationException("Validation failed", e);
        }
        catch (ParserConfigurationException pce)
        {
            throw new ConfigurationException("Validation failed", pce);
        }
        finally
        {
            endWrite();
        }
    }

    /**
     * Creates and initializes the transformer used for save operations. This
     * base implementation initializes all of the default settings like
     * indention mode and the DOCTYPE. Derived classes may overload this method
     * if they have specific needs.
     *
     * @return the transformer to use for a save operation
     * @throws TransformerException if an error occurs
     * @since 1.3
     */
    protected Transformer createTransformer() throws TransformerException
    {
        Transformer transformer = TransformerFactory.newInstance()
                .newTransformer();

        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        if (locator.getEncoding() != null)
        {
            transformer.setOutputProperty(OutputKeys.ENCODING, locator.getEncoding());
        }
        if (publicID != null)
        {
            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC,
                    publicID);
        }
        if (systemID != null)
        {
            transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,
                    systemID);
        }

        return transformer;
    }

    /**
     * Creates a copy of this object. The new configuration object will contain
     * the same properties as the original, but it will lose any connection to a
     * source document (if one exists). This is to avoid race conditions if both
     * the original and the copy are modified and then saved.
     *
     * @return the copy
     */
    @Override
    public Object clone()
    {
        XMLConfiguration copy = (XMLConfiguration) super.clone();

        // clear document related properties
        copy.document = null;
        // clear all references in the nodes, too
        clearReferences(copy.getRootNode());

        return copy;
    }

    /**
     * Adds a collection of nodes directly to this configuration. This
     * implementation ensures that the nodes to be added are of the correct node
     * type (they have to be converted to {@code XMLNode} if necessary).
     *
     * @param key the key where the nodes are to be added
     * @param nodes the collection with the new nodes
     * @since 1.5
     */
    @Override
    protected void addNodesInternal(String key, Collection<? extends ConfigurationNode> nodes)
    {
        if (nodes != null && !nodes.isEmpty())
        {
            Collection<XMLNode> xmlNodes;
            xmlNodes = new ArrayList<XMLNode>(nodes.size());
            for (ConfigurationNode node : nodes)
            {
                xmlNodes.add(convertToXMLNode(node));
            }
            super.addNodesInternal(key, xmlNodes);
        }
        else
        {
            super.addNodesInternal(key, nodes);
        }
    }

    /**
     * Converts the specified node into a {@code XMLNode} if necessary.
     * This is required for nodes that are directly added, e.g. by
     * {@code addNodes()}. If the passed in node is already an instance
     * of {@code XMLNode}, it is directly returned, and conversion
     * stops. Otherwise a new {@code XMLNode} is created, and the
     * children are also converted.
     *
     * @param node the node to be converted
     * @return the converted node
     */
    private XMLNode convertToXMLNode(ConfigurationNode node)
    {
        if (node instanceof XMLNode)
        {
            return (XMLNode) node;
        }

        XMLNode nd = (XMLNode) createNode(node.getName());
        nd.setValue(node.getValue());
        nd.setAttribute(node.isAttribute());
        for (ConfigurationNode child : node.getChildren())
        {
            nd.addChild(convertToXMLNode(child));
        }
        for (ConfigurationNode attr : node.getAttributes())
        {
            nd.addAttribute(convertToXMLNode(attr));
        }
        return nd;
    }

    /**
     * <p>
     * Registers the specified DTD URL for the specified public identifier.
     * </p>
     * <p>
     * {@code XMLConfiguration} contains an internal
     * {@code EntityResolver} implementation. This maps
     * {@code PUBLICID}'s to URLs (from which the resource will be
     * loaded). A common use case for this method is to register local URLs
     * (possibly computed at runtime by a class loader) for DTDs. This allows
     * the performance advantage of using a local version without having to
     * ensure every {@code SYSTEM} URI on every processed XML document is
     * local. This implementation provides only basic functionality. If more
     * sophisticated features are required, using
     * {@link #setDocumentBuilder(DocumentBuilder)} to set a custom
     * {@code DocumentBuilder} (which also can be initialized with a
     * custom {@code EntityResolver}) is recommended.
     * </p>
     * <p>
     * <strong>Note:</strong> This method will have no effect when a custom
     * {@code DocumentBuilder} has been set. (Setting a custom
     * {@code DocumentBuilder} overrides the internal implementation.)
     * </p>
     * <p>
     * <strong>Note:</strong> This method must be called before the
     * configuration is loaded. So the default constructor of
     * {@code XMLConfiguration} should be used, the location of the
     * configuration file set, {@code registerEntityId()} called, and
     * finally the {@code load()} method can be invoked.
     * </p>
     *
     * @param publicId Public identifier of the DTD to be resolved
     * @param entityURL The URL to use for reading this DTD
     * @throws IllegalArgumentException if the public ID is undefined
     * @since 1.5
     */
    @Override
    public void registerEntityId(String publicId, URL entityURL)
    {
        if (entityResolver instanceof EntityRegistry)
        {
            ((EntityRegistry) entityResolver).registerEntityId(publicId, entityURL);
        }
    }

    /**
     * Resolves the requested external entity. This is the default
     * implementation of the {@code EntityResolver} interface. It checks
     * the passed in public ID against the registered entity IDs and uses a
     * local URL if possible.
     *
     * @param publicId the public identifier of the entity being referenced
     * @param systemId the system identifier of the entity being referenced
     * @return an input source for the specified entity
     * @throws SAXException if a parsing exception occurs
     * @since 1.5
     * @deprecated Use getEntityResolver().resolveEntity()
     */
    @Override
    @Deprecated
    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException
    {
        try
        {
            return entityResolver.resolveEntity(publicId, systemId);
        }
        catch (IOException e)
        {
            throw new SAXException(e);
        }
    }

    /**
     * Returns a map with the entity IDs that have been registered using the
     * {@code registerEntityId()} method.
     *
     * @return a map with the registered entity IDs
     */
    @Override
    public Map<String, URL> getRegisteredEntities()
    {
        if (entityResolver instanceof EntityRegistry)
        {
            return ((EntityRegistry) entityResolver).getRegisteredEntities();
        }
        return new HashMap<String, URL>();
    }

    /**
     * A specialized {@code Node} class that is connected with an XML
     * element. Changes on a node are also performed on the associated element.
     */
    class XMLNode extends DefaultConfigurationNode
    {
        /**
         * The serial version UID.
         */
        private static final long serialVersionUID = -4133988932174596562L;

        /**
         * Creates a new instance of {@code XMLNode} and initializes it
         * with a name and the corresponding XML element.
         *
         * @param name the node's name
         * @param elem the XML element
         */
        public XMLNode(String name, Element elem)
        {
            super(name);
            setReference(elem);
        }

        /**
         * Sets the value of this node. If this node is associated with an XML
         * element, this element will be updated, too.
         *
         * @param value the node's new value
         */
        @Override
        public void setValue(Object value)
        {
            super.setValue(value);

            if (getReference() != null && document != null)
            {
                if (isAttribute())
                {
                    updateAttribute();
                }
                else
                {
                    updateElement(value);
                }
            }
        }

        /**
         * Updates the associated XML elements when a node is removed.
         */
        @Override
        protected void removeReference()
        {
            if (getReference() != null)
            {
                Element element = (Element) getReference();
                if (isAttribute())
                {
                    updateAttribute();
                }
                else
                {
                    org.w3c.dom.Node parentElem = element.getParentNode();
                    if (parentElem != null)
                    {
                        parentElem.removeChild(element);
                    }
                }
            }
        }

        /**
         * Updates the node's value if it represents an element node.
         *
         * @param value the new value
         */
        private void updateElement(Object value)
        {
            Text txtNode = findTextNodeForUpdate();
            if (value == null)
            {
                // remove text
                if (txtNode != null)
                {
                    ((Element) getReference()).removeChild(txtNode);
                }
            }
            else
            {
                String newValue =
                        String.valueOf(getListDelimiterHandler().escape(value,
                                ListDelimiterHandler.NOOP_TRANSFORMER));
                if (txtNode == null)
                {
                    txtNode = document.createTextNode(newValue);
                    if (((Element) getReference()).getFirstChild() != null)
                    {
                        ((Element) getReference()).insertBefore(txtNode,
                                ((Element) getReference()).getFirstChild());
                    }
                    else
                    {
                        ((Element) getReference()).appendChild(txtNode);
                    }
                }
                else
                {
                    txtNode.setNodeValue(newValue);
                }
            }
        }

        /**
         * Updates the node's value if it represents an attribute.
         *
         */
        private void updateAttribute()
        {
            XMLBuilderVisitor.updateAttribute(getParentNode(), getName());
        }

        /**
         * Returns the only text node of this element for update. This method is
         * called when the element's text changes. Then all text nodes except
         * for the first are removed. A reference to the first is returned or
         * <b>null </b> if there is no text node at all.
         *
         * @return the first and only text node
         */
        private Text findTextNodeForUpdate()
        {
            Text result = null;
            Element elem = (Element) getReference();
            // Find all Text nodes
            NodeList children = elem.getChildNodes();
            Collection<org.w3c.dom.Node> textNodes = new ArrayList<org.w3c.dom.Node>();
            for (int i = 0; i < children.getLength(); i++)
            {
                org.w3c.dom.Node nd = children.item(i);
                if (nd instanceof Text)
                {
                    if (result == null)
                    {
                        result = (Text) nd;
                    }
                    else
                    {
                        textNodes.add(nd);
                    }
                }
            }

            // We don't want CDATAs
            if (result instanceof CDATASection)
            {
                textNodes.add(result);
                result = null;
            }

            // Remove all but the first Text node
            for (org.w3c.dom.Node tn : textNodes)
            {
                elem.removeChild(tn);
            }
            return result;
        }
    }

    /**
     * A concrete {@code BuilderVisitor} that can construct XML
     * documents.
     */
    static class XMLBuilderVisitor extends BuilderVisitor
    {
        /** Stores the document to be constructed. */
        private final Document document;

        /** Stores the list delimiter handler .*/
        private final ListDelimiterHandler listDelimiterHandler;

        /**
         * Creates a new instance of {@code XMLBuilderVisitor}.
         *
         * @param doc the document to be created
         * @param handler the delimiter handler for properties with multiple values
         */
        public XMLBuilderVisitor(Document doc, ListDelimiterHandler handler)
        {
            document = doc;
            listDelimiterHandler = handler;
        }

        /**
         * Processes the node hierarchy and adds new nodes to the document.
         *
         * @param rootNode the root node
         */
        public void processDocument(ConfigurationNode rootNode)
        {
            rootNode.visit(this);
        }

        /**
         * Inserts a new node. This implementation ensures that the correct
         * XML element is created and inserted between the given siblings.
         *
         * @param newNode the node to insert
         * @param parent the parent node
         * @param sibling1 the first sibling
         * @param sibling2 the second sibling
         * @return the new node
         */
        @Override
        protected Object insert(ConfigurationNode newNode,
                ConfigurationNode parent, ConfigurationNode sibling1,
                ConfigurationNode sibling2)
        {
            if (newNode.isAttribute())
            {
                updateAttribute(parent, getElement(parent), newNode.getName());
                return null;
            }

            else
            {
                Element elem = document.createElement(newNode.getName());
                if (newNode.getValue() != null)
                {
                    String txt =
                            String.valueOf(listDelimiterHandler.escape(
                                    newNode.getValue(),
                                    ListDelimiterHandler.NOOP_TRANSFORMER));
                    elem.appendChild(document.createTextNode(txt));
                }
                if (sibling2 == null)
                {
                    getElement(parent).appendChild(elem);
                }
                else if (sibling1 != null)
                {
                    getElement(parent).insertBefore(elem, getElement(sibling1).getNextSibling());
                }
                else
                {
                    getElement(parent).insertBefore(elem, getElement(parent).getFirstChild());
                }
                return elem;
            }
        }

        /**
         * Helper method for updating the value of the specified node's
         * attribute with the given name.
         *
         * @param node the affected node
         * @param elem the element that is associated with this node
         * @param name the name of the affected attribute
         */
        private static void updateAttribute(ConfigurationNode node, Element elem, String name)
        {
            if (node != null && elem != null)
            {
                List<ConfigurationNode> definedAttrs =
                        fetchDefinedAttributes(node.getAttributes(name));

                if (!definedAttrs.isEmpty())
                {
                    if (definedAttrs.size() > 1)
                    {
                        throw new ConfigurationRuntimeException(
                                "Multiple values for attribute '" + name
                                        + "' are not supported!");
                    }
                    ConfigurationNode attr = definedAttrs.get(0);
                    elem.setAttribute(name, attr.getValue().toString());
                }
                else
                {
                    elem.removeAttribute(name);
                }
            }
        }

        /**
         * Returns a list containing only attribute nodes with a defined value.
         *
         * @param attrs the list with all attributes
         * @return a list with the defined attributes
         */
        private static List<ConfigurationNode> fetchDefinedAttributes(
                List<ConfigurationNode> attrs)
        {
            if (attrs.isEmpty())
            {
                return attrs;
            }

            List<ConfigurationNode> definedAttrs =
                    new ArrayList<ConfigurationNode>(attrs.size());
            for (ConfigurationNode attr : attrs)
            {
                if (attr.getValue() != null)
                {
                    definedAttrs.add(attr);
                }
            }
            return definedAttrs;
        }

        /**
         * Updates the value of the specified attribute of the given node.
         * Because there can be multiple child nodes representing this attribute
         * the new value is determined by iterating over all those child nodes.
         *
         * @param node the affected node
         * @param name the name of the attribute
         */
        static void updateAttribute(ConfigurationNode node, String name)
        {
            if (node != null)
            {
                updateAttribute(node, (Element) node.getReference(), name);
            }
        }

        /**
         * Helper method for accessing the element of the specified node.
         *
         * @param node the node
         * @return the element of this node
         */
        private Element getElement(ConfigurationNode node)
        {
            // special treatment for root node of the hierarchy
            return (node.getName() != null && node.getReference() != null) ? (Element) node
                    .getReference()
                    : document.getDocumentElement();
        }
    }
}
