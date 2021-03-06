/* Copyright 2014-2017 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.collector.core.checksum.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.checksum.AbstractDocumentChecksummer;
import com.norconex.collector.core.checksum.IDocumentChecksummer;
import com.norconex.collector.core.doc.CollectorMetadata;
import com.norconex.commons.lang.config.XMLConfigurationUtil;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;
import com.norconex.importer.doc.ImporterDocument;
import com.norconex.importer.doc.ImporterMetadata;

/**
 * <p>Implementation of {@link IDocumentChecksummer} which 
 * returns a MD5 checksum value of the extracted document content unless
 * one or more given source fields are specified, in which case the MD5 
 * checksum value is constructed from those fields.  This checksum is normally 
 * performed right after the document has been imported.
 * </p>
 * <p>
 * You have the option to keep the checksum as a document metadata field. 
 * When {@link #setKeep(boolean)} is <code>true</code>, the checksum will be
 * stored in the target field name specified. If you do not specify any,
 * it stores it under the metadata field name 
 * {@link CollectorMetadata#COLLECTOR_CHECKSUM_METADATA}. 
 * </p>
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;documentChecksummer 
 *      class="com.norconex.collector.core.checksum.impl.MD5DocumentChecksummer"
 *      disabled="[false|true]"
 *      keep="[false|true]"
 *      targetField="(optional metadata field to store the checksum)"&gt;
 *    &lt;sourceFields&gt;
 *        (optional coma-separated list fields used to create checksum)
 *    &lt;/sourceFields&gt;
 *  &lt;/documentChecksummer&gt;
 * </pre>
 * <p>
 * <code>targetField</code> is ignored unless the <code>keep</code> 
 * attribute is set to <code>true</code>.
 * </p>
 * <p>
 * This implementation can be disabled in your 
 * configuration by specifying <code>disabled="true"</code>. When disabled,
 * the checksum returned is always <code>null</code>.  
 * </p>
 * 
 * <h4>Usage example:</h4>
 * <p>
 * The following uses the document body (default) to make the checksum.
 * </p> 
 * <pre>
 *  &lt;documentChecksummer 
 *      class="com.norconex.collector.core.checksum.impl.MD5DocumentChecksummer" /&gt;
 * </pre> 
 * 
 * @author Pascal Essiembre
 */
public class MD5DocumentChecksummer extends AbstractDocumentChecksummer {

	private static final Logger LOG = LogManager.getLogger(
			MD5DocumentChecksummer.class);
    
	private String[] sourceFields = null;
	private boolean disabled;
	
    @Override
    public String doCreateDocumentChecksum(ImporterDocument document) {
        if (disabled) {
            return null;
        }

        // If fields are specified, perform checksum on them.
		if (ArrayUtils.isNotEmpty(sourceFields)) {
		    ImporterMetadata meta = document.getMetadata();
		    StringBuilder b = new StringBuilder();
 
		    List<String> fields = 
		            new ArrayList<String>(Arrays.asList(sourceFields));
            // Sort to make sure field order does not affect checksum.
		    Collections.sort(fields);
            for (String field : fields) {
                List<String> values = meta.getStrings(field);
                if (values != null) {
                    for (String value : values) {
                        if (StringUtils.isNotBlank(value)) {
                            b.append(field).append('=');
                            b.append(value).append(';');
                        }
                    }
                }
            }
            String combinedValues = b.toString();
            if (StringUtils.isNotBlank(combinedValues)) {
                String checksum = DigestUtils.md5Hex(b.toString());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Document checksum from "
                            + StringUtils.join(sourceFields, ',')
                            + " : " + checksum);
                }
                return checksum;
            }
            return null;
    	}
		
        // If field is not specified, perform checksum on whole text file.
		try {
		    InputStream is = document.getContent();
	    	String checksum = DigestUtils.md5Hex(is);
			LOG.debug("Document checksum from content: " + checksum);
	    	is.close();
	    	return checksum;
		} catch (IOException e) {
			throw new CollectorException("Cannot create document checksum on : " 
			        + document.getReference(), e);
		}
    }

	/**
     * Gets the fields used to construct a MD5 checksum.  Default
     * is <code>null</code> (checksum is performed on entire content).
     * @return fields to use to construct the checksum
     * @since 1.2.0
     */
    public String[] getSourceFields() {
        return sourceFields;
    }
    /**
     * Sets the fields used to construct a MD5 checksum.  Specifying
     * <code>null</code> means all content will be used.
     * @param fields fields to use to construct the checksum
     * @since 1.2.0
     */
    public void setSourceFields(String... fields) {
        this.sourceFields = fields;
    }

	/**
	 * Whether this checksummer is disabled or not. When disabled, not
	 * checksum will be created (the checksum will be <code>null</code>).
	 * @return <code>true</code> if disabled
	 */
	public boolean isDisabled() {
        return disabled;
    }
	/**
	 * Sets whether this checksummer is disabled or not. When disabled, not
     * checksum will be created (the checksum will be <code>null</code>).
	 * @param disabled <code>true</code> if disabled
	 */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @Override
	protected void loadChecksummerFromXML(XMLConfiguration xml) {
        setDisabled(xml.getBoolean("[@disabled]", disabled));
        setSourceFields(XMLConfigurationUtil.getCSVStringArray(
                xml, "sourceFields", getSourceFields()));
    }
	@Override
	protected void saveChecksummerToXML(EnhancedXMLStreamWriter writer)
	        throws XMLStreamException {
        writer.writeAttributeBoolean("disabled", isDisabled());
        writer.writeElementString(
                "sourceFields", StringUtils.join(sourceFields, ','));
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof MD5DocumentChecksummer)) {
            return false;
        }
        MD5DocumentChecksummer castOther = (MD5DocumentChecksummer) other;
        return new EqualsBuilder()
                .appendSuper(super.equals(castOther))
                .append(disabled, castOther.disabled)
                .append(sourceFields, castOther.sourceFields)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .appendSuper(super.hashCode())
                .append(disabled)
                .append(sourceFields)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("disabled", disabled)
                .append("sourceFields", sourceFields)
                .toString();
    }
}
