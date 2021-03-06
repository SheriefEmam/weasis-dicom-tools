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
package org.weasis.dicom;

import java.io.File;
import java.io.FileInputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.net.Status;
import org.dcm4che3.util.UIDUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.junit.Assert;
import org.junit.Test;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.web.Multipart;
import org.weasis.dicom.web.StowrsMultiFiles;
import org.weasis.dicom.web.StowrsSingleFile;
import org.weasis.dicom.web.UploadSingleFile;

public class StowNetTest {

    @Test
    public void testProcess() {
        BasicConfigurator.configure();

        List<String> files = new ArrayList<>();
        try {
            files.add(new File(getClass().getResource("mr.dcm").toURI()).getPath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        String stowService = "http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies";
        DicomState state = null;

        // Upload files
        try (StowrsMultiFiles stowRS = new StowrsMultiFiles(stowService, Multipart.ContentType.DICOM, null, null)) {
            state = stowRS.uploadDicom(files, true);
        } catch (Exception e) {
            System.out.println("StowRS error: " + e.getMessage());
        }

        MatcherAssert.assertThat("DicomState cannot be null", state, IsNull.notNullValue());
        MatcherAssert.assertThat(state.getMessage(), state.getStatus(), IsEqual.equalTo(Status.Success));
        
        String message = null;
        // Upload a modify file
        try (UploadSingleFile stowRS = new StowrsSingleFile(stowService, Multipart.ContentType.DICOM);
                        DicomInputStream in = new DicomInputStream(new FileInputStream(files.get(0)))) {
            in.setIncludeBulkData(IncludeBulkData.URI);
            Attributes attributes = in.readDataset(-1, -1);
            attributes.setString(Tag.PatientName, VR.PN, "Override^Patient^Name");
            attributes.setString(Tag.PatientID, VR.LO, "ModifiedPatientID");
            attributes.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
            attributes.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
            attributes.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());

            stowRS.uploadDicom(attributes, in.getTransferSyntax());
        } catch (Exception e) {
            message = e.getMessage();
        }
        MatcherAssert.assertThat(message, message, IsNull.nullValue());
    }

}
