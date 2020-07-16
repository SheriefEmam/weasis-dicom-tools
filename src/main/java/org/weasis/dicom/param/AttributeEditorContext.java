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
package org.weasis.dicom.param;

import org.dcm4che6.img.op.MaskArea;

public class AttributeEditorContext {

    /**
     * Abort status allows to skip the file transfer or abort the DICOM association
     *
     */
    public enum Abort {
        // Do nothing
        NONE,
        // Allows to skip the bulk data transfer to go to the next file
        FILE_EXCEPTION,
        // Stop the DICOM connection. Attention, this will abort other transfers when there are several destinations for one source.
        CONNECTION_EXCEPTION
    }
    
    private final DicomNode sourceNode;
    private final DicomNode destinationNode;

    private Abort abort;
    private String abortMessage;
    private MaskArea maskArea;

    public AttributeEditorContext(DicomNode sourceNode, DicomNode destinationNode) {
        this.sourceNode = sourceNode;
        this.destinationNode = destinationNode;
        this.abort = Abort.NONE;
    }

    public Abort getAbort() {
        return abort;
    }

    public void setAbort(Abort abort) {
        this.abort = abort;
    }

    public String getAbortMessage() {
        return abortMessage;
    }

    public void setAbortMessage(String abortMessage) {
        this.abortMessage = abortMessage;
    }

    public DicomNode getSourceNode() {
        return sourceNode;
    }

    public DicomNode getDestinationNode() {
        return destinationNode;
    }

    public MaskArea getMaskArea() {
        return maskArea;
    }

    public void setMaskArea(MaskArea maskArea) {
        this.maskArea = maskArea;
    }
}
