/*******************************************************************************
 * Copyright (c) 2009-2019 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.web;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.util.Map;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomEncoding;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.json.JSONWriter;
import org.dcm4che6.xml.SAXWriter;
import org.weasis.core.util.FileUtil;

public class StowrsSingleFile extends AbstractStowrs implements UploadSingleFile {

    public StowrsSingleFile(String requestURL, Multipart.ContentType contentType) throws IOException {
        this(requestURL, contentType, null, null);
    }

    public StowrsSingleFile(String requestURL, Multipart.ContentType contentType, String agentName,
        Map<String, String> headers) {
        super(requestURL, contentType, agentName, headers);
    }

    @Override
    public void uploadDicom(InputStream in, DicomObject fmi, String tsuid, String iuid) throws IOException {
        HttpURLConnection httpPost = buildConnection();
        try (DataOutputStream out = new DataOutputStream(httpPost.getOutputStream());
                        DicomOutputStream dos = new DicomOutputStream(out).withEncoding(DicomEncoding.of(tsuid))) {
            writeContentMarkers(out);
            dos.writeFileMetaInformation(fmi);

            byte[] buf = new byte[FileUtil.FILE_BUFFER];
            int offset;
            while ((offset = in.read(buf)) > 0) {
                dos.write(buf, 0, offset);
            }
            writeEndMarkers(httpPost, out, iuid);
        } finally {
            removeConnection(httpPost);
        }
    }

    @Override
    public void uploadDicom(DicomObject metadata, String tsuid) throws IOException {
        HttpURLConnection httpPost = buildConnection();
        try (DataOutputStream out = new DataOutputStream(httpPost.getOutputStream());
                        DicomOutputStream dos = new DicomOutputStream(out).withEncoding(DicomEncoding.of(tsuid))) {
            writeContentMarkers(out);
            DicomObject fmi = metadata.createFileMetaInformation(tsuid);
            dos.writeFileMetaInformation(fmi);
            dos.writeDataSet(metadata);
            writeEndMarkers(httpPost, out, metadata.getString(Tag.SOPInstanceUID).orElseThrow());
        } finally {
            removeConnection(httpPost);
        }
    }

    @Override
    public void uploadEncapsulatedDocument(DicomObject metadata, File bulkDataFile, String mimeType, String sopClassUID)
        throws Exception {
        HttpURLConnection httpPost = buildConnection();

        setEncapsulatedDocumentAttributes(bulkDataFile.toPath(), metadata, mimeType);
        if (metadata.get(Tag.EncapsulatedDocument).isEmpty()) {
            metadata.setBulkData(Tag.EncapsulatedDocument, VR.OB, bulkDataFile.toURI().toASCIIString(), null);
        }
        metadata.setString(Tag.SOPClassUID, VR.UI, sopClassUID);
        ensureUID(metadata, Tag.StudyInstanceUID);
        ensureUID(metadata, Tag.SeriesInstanceUID);
        ensureUID(metadata, Tag.SOPInstanceUID);

        try (ByteArrayOutputStream bOut = new ByteArrayOutputStream();
                        DataOutputStream out = new DataOutputStream(httpPost.getOutputStream())) {
            if (getContentType() == Multipart.ContentType.JSON)
                try (JsonGenerator gen = Json.createGenerator(bOut)) {
                    new JSONWriter(gen, bOut).writeDataSet(metadata);
                }
            else {
                TransformerHandler th = getTransformerHandler(null);
                Transformer t = th.getTransformer();
                t.setOutputProperty(OutputKeys.INDENT, "no");
                t.setOutputProperty(OutputKeys.VERSION, "1.0");
                th.setResult(new StreamResult(bOut));
                SAXWriter handler = new SAXWriter(th);
                handler.writeDataSet(metadata);
            }

            writeContentMarkers(out);

            out.write(bOut.toByteArray());

            // Segment and headers for a part
            out.write(Multipart.Separator.BOUNDARY.getType());
            out.writeBytes(MULTIPART_BOUNDARY);
            byte[] fsep = Multipart.Separator.FIELD.getType();
            out.write(fsep);
            out.writeBytes("Content-Type: "); //$NON-NLS-1$
            out.writeBytes(mimeType);
            out.write(fsep);
            out.writeBytes("Content-Location: "); //$NON-NLS-1$
            out.writeBytes(getContentLocation(metadata));
            out.write(Multipart.Separator.HEADER.getType());

            Files.copy(bulkDataFile.toPath(), out);

            writeEndMarkers(httpPost, out, metadata.getString(Tag.SOPInstanceUID).orElseThrow());
        } finally {
            removeConnection(httpPost);
        }
    }
}
