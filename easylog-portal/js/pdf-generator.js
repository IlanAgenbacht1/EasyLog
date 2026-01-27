/**
 * PDF generation for job cards (time logs)
 * Uses pdf-lib for client-side PDF generation
 */

class JobCardPDFGenerator {
  constructor(firebaseStorage = null) {
    this.PDFLib = window.PDFLib;
    if (!this.PDFLib) {
      throw new Error('pdf-lib not loaded. Please include pdf-lib script in HTML.');
    }
    // Store Firebase Storage reference for authenticated downloads
    this.storage = firebaseStorage;
  }

  /**
   * Generate a single job card PDF - Professional single-page layout
   * @param {Object} timelog - Time log data from Firestore
   * @param {Object} customerData - Customer data from Firestore
   * @param {Array<string>} photoUrls - Array of photo download URLs
   * @returns {Promise<Uint8Array>} PDF bytes
   */
  async generateJobCard(timelog, customerData, photoUrls = []) {
    const pdfDoc = await this.PDFLib.PDFDocument.create();
    const page = pdfDoc.addPage([595, 842]); // A4 portrait
    const { width, height } = page.getSize();

    // Fonts
    const regular = await pdfDoc.embedFont(this.PDFLib.StandardFonts.Helvetica);
    const bold = await pdfDoc.embedFont(this.PDFLib.StandardFonts.HelveticaBold);

    // Layout constants
    const margin = 40;
    const colGap = 20;
    const col1X = margin;
    const col2X = width / 2 + colGap / 2;
    const colWidth = (width - 2 * margin - colGap) / 2;

    // Colors
    const primaryBlue = this.PDFLib.rgb(0.18, 0.31, 0.56); // Professional blue
    const darkGray = this.PDFLib.rgb(0.2, 0.2, 0.2);
    const mediumGray = this.PDFLib.rgb(0.4, 0.4, 0.4);
    const lightGray = this.PDFLib.rgb(0.7, 0.7, 0.7);
    const borderGray = this.PDFLib.rgb(0.85, 0.85, 0.85);

    let y = height - margin;

    // === HEADER WITH ACCENT BAR ===
    // Blue accent bar at top
    page.drawRectangle({
      x: 0,
      y: height - 8,
      width: width,
      height: 8,
      color: primaryBlue,
    });

    y -= 30;

    // Title and Job ID
    page.drawText('JOB CARD', {
      x: margin,
      y: y,
      size: 22,
      font: bold,
      color: darkGray,
    });

    const jobId = timelog.localId || timelog.id;
    const jobIdText = `#${jobId.substring(0, 8)}`;
    page.drawText(jobIdText, {
      x: width - margin - bold.widthOfTextAtSize(jobIdText, 11),
      y: y + 3,
      size: 11,
      font: bold,
      color: primaryBlue,
    });

    y -= 8;
    const genText = `Generated: ${new Date().toLocaleDateString()}`;
    page.drawText(genText, {
      x: width - margin - regular.widthOfTextAtSize(genText, 8),
      y: y,
      size: 8,
      font: regular,
      color: mediumGray,
    });

    y -= 25;

    // Horizontal line
    page.drawLine({
      start: { x: margin, y: y },
      end: { x: width - margin, y: y },
      thickness: 1,
      color: borderGray,
    });

    y -= 20;

    // === TWO-COLUMN LAYOUT FOR CUSTOMER & JOB INFO ===
    const startY = y;

    // LEFT COLUMN - Customer Information
    page.drawText('CUSTOMER', {
      x: col1X,
      y: y,
      size: 11,
      font: bold,
      color: primaryBlue,
    });
    y -= 16;

    this._drawCompactKeyValue(page, 'Name', timelog.customerName, col1X, y, colWidth, regular, bold);
    y -= 12;
    this._drawCompactKeyValue(page, 'Address', customerData?.address || '-', col1X, y, colWidth, regular, bold);
    y -= 12;
    this._drawCompactKeyValue(page, 'Contact', customerData?.contactName || '-', col1X, y, colWidth, regular, bold);
    y -= 12;
    this._drawCompactKeyValue(page, 'Phone', customerData?.contactPhone || '-', col1X, y, colWidth, regular, bold);

    // RIGHT COLUMN - Job Details
    y = startY;
    page.drawText('JOB DETAILS', {
      x: col2X,
      y: y,
      size: 11,
      font: bold,
      color: primaryBlue,
    });
    y -= 16;

    const startDate = timelog.timeStarted?.toDate ? timelog.timeStarted.toDate() : new Date(timelog.timeStarted);
    const endDate = timelog.timeEnded?.toDate ? timelog.timeEnded.toDate() : new Date(timelog.timeEnded);

    this._drawCompactKeyValue(page, 'Engineer', timelog.engineerName, col2X, y, colWidth, regular, bold);
    y -= 12;
    this._drawCompactKeyValue(page, 'Date', startDate.toLocaleDateString(), col2X, y, colWidth, regular, bold);
    y -= 12;
    this._drawCompactKeyValue(page, 'Started', startDate.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}), col2X, y, colWidth, regular, bold);
    y -= 12;
    this._drawCompactKeyValue(page, 'Ended', endDate.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}), col2X, y, colWidth, regular, bold);

    y -= 20;

    // === TIME SUMMARY BOX ===
    const boxY = y - 50;
    page.drawRectangle({
      x: margin,
      y: boxY,
      width: width - 2 * margin,
      height: 48,
      borderColor: borderGray,
      borderWidth: 1,
      color: this.PDFLib.rgb(0.98, 0.98, 0.98),
    });

    const boxContentY = boxY + 32;
    const boxItemWidth = (width - 2 * margin) / 3;

    // Travel Time
    page.drawText('TRAVEL TIME', {
      x: margin + 10,
      y: boxContentY,
      size: 8,
      font: regular,
      color: mediumGray,
    });
    page.drawText(`${timelog.travelTimeMinutes || 0} min`, {
      x: margin + 10,
      y: boxContentY - 14,
      size: 12,
      font: bold,
      color: darkGray,
    });

    // On-Site Time
    page.drawText('ON-SITE TIME', {
      x: margin + boxItemWidth + 10,
      y: boxContentY,
      size: 8,
      font: regular,
      color: mediumGray,
    });
    page.drawText(`${timelog.timeOnSiteMinutes || 0} min`, {
      x: margin + boxItemWidth + 10,
      y: boxContentY - 14,
      size: 12,
      font: bold,
      color: darkGray,
    });

    // Total Billable
    const totalMinutes = (timelog.travelTimeMinutes || 0) + (timelog.timeOnSiteMinutes || 0);
    const billableHours = (totalMinutes / 60).toFixed(2);
    page.drawText('TOTAL BILLABLE', {
      x: margin + 2 * boxItemWidth + 10,
      y: boxContentY,
      size: 8,
      font: regular,
      color: mediumGray,
    });
    page.drawText(`${billableHours} hrs`, {
      x: margin + 2 * boxItemWidth + 10,
      y: boxContentY - 14,
      size: 12,
      font: bold,
      color: primaryBlue,
    });

    y = boxY - 15;

    // === WORK DESCRIPTION ===
    page.drawText('WORK PERFORMED', {
      x: margin,
      y: y,
      size: 11,
      font: bold,
      color: primaryBlue,
    });
    y -= 14;

    const descLines = this._wrapText(timelog.workDescription || 'No description provided', 100, regular, 9);
    const maxDescLines = 4; // Limit description to save space
    for (let i = 0; i < Math.min(descLines.length, maxDescLines); i++) {
      page.drawText(descLines[i], {
        x: margin,
        y: y,
        size: 9,
        font: regular,
        color: darkGray,
      });
      y -= 11;
    }
    if (descLines.length > maxDescLines) {
      page.drawText('...', {
        x: margin,
        y: y,
        size: 9,
        font: regular,
        color: mediumGray,
      });
      y -= 11;
    }

    y -= 10;

    // === PHOTOS AND SIGNATURE SECTION ===
    // Calculate optimal layout for photos
    const maxPhotos = Math.min(photoUrls.length, 6); // Maximum 6 photos
    const photosPerRow = 3;
    const photoSpacing = 8;
    const photoWidth = (width - 2 * margin - 2 * photoSpacing) / 3;
    const photoHeight = photoWidth * 0.75; // 4:3 aspect ratio

    // Signature dimensions
    const sigWidth = 140;
    const sigHeight = 70;
    const sigX = width - margin - sigWidth;
    const sigY = y - sigHeight - 20;

    if (maxPhotos > 0) {
      page.drawText('SITE PHOTOS', {
        x: margin,
        y: y,
        size: 11,
        font: bold,
        color: primaryBlue,
      });

      // Load and embed photos
      let photoIndex = 0;
      let currentPhotoY = y - 16;

      for (let row = 0; row < 2 && photoIndex < maxPhotos; row++) {
        for (let col = 0; col < photosPerRow && photoIndex < maxPhotos; col++) {
          if (currentPhotoY - photoHeight < 70) break; // Stop if too close to footer

          try {
            const photoUrl = photoUrls[photoIndex];
            const photoBytes = await this._fetchImageBytes(photoUrl);

            let photoImage;
            try {
              photoImage = await pdfDoc.embedJpg(photoBytes);
            } catch {
              photoImage = await pdfDoc.embedPng(photoBytes);
            }

            const photoX = margin + col * (photoWidth + photoSpacing);

            // Draw photo border
            page.drawRectangle({
              x: photoX,
              y: currentPhotoY - photoHeight,
              width: photoWidth,
              height: photoHeight,
              borderColor: borderGray,
              borderWidth: 1,
            });

            // Draw photo (fit within bounds)
            const imgAspect = photoImage.width / photoImage.height;
            const boxAspect = photoWidth / photoHeight;
            let drawWidth, drawHeight, drawX, drawY;

            if (imgAspect > boxAspect) {
              drawWidth = photoWidth - 4;
              drawHeight = drawWidth / imgAspect;
              drawX = photoX + 2;
              drawY = currentPhotoY - photoHeight / 2 - drawHeight / 2;
            } else {
              drawHeight = photoHeight - 4;
              drawWidth = drawHeight * imgAspect;
              drawX = photoX + photoWidth / 2 - drawWidth / 2;
              drawY = currentPhotoY - photoHeight + 2;
            }

            page.drawImage(photoImage, {
              x: drawX,
              y: drawY,
              width: drawWidth,
              height: drawHeight,
            });

            photoIndex++;
          } catch (error) {
            console.error(`Failed to embed photo ${photoIndex + 1}:`, error);
            photoIndex++;
          }
        }
        currentPhotoY -= (photoHeight + photoSpacing);
      }

      if (photoUrls.length > maxPhotos) {
        page.drawText(`+${photoUrls.length - maxPhotos} more in portal`, {
          x: margin,
          y: currentPhotoY - 5,
          size: 8,
          font: regular,
          color: mediumGray,
        });
      }
    }

    // === SIGNATURE (Positioned in bottom-right area) ===
    if (timelog.signatureUrl || timelog.signaturePath) {
      page.drawText('CUSTOMER SIGNATURE', {
        x: sigX,
        y: sigY + sigHeight + 8,
        size: 9,
        font: bold,
        color: primaryBlue,
      });

      try {
        const signatureUrl = timelog.signatureUrl;

        if (signatureUrl) {
          const signatureBytes = await this._fetchImageBytes(signatureUrl);

          let signatureImage;
          try {
            signatureImage = await pdfDoc.embedPng(signatureBytes);
          } catch {
            signatureImage = await pdfDoc.embedJpg(signatureBytes);
          }

          // Draw signature box
          page.drawRectangle({
            x: sigX,
            y: sigY,
            width: sigWidth,
            height: sigHeight,
            borderColor: borderGray,
            borderWidth: 1,
            color: this.PDFLib.rgb(1, 1, 1),
          });

          // Draw signature image (fit within box)
          const sigAspect = signatureImage.width / signatureImage.height;
          const boxSigAspect = sigWidth / sigHeight;
          let sigDrawWidth, sigDrawHeight, sigDrawX, sigDrawY;

          if (sigAspect > boxSigAspect) {
            sigDrawWidth = sigWidth - 8;
            sigDrawHeight = sigDrawWidth / sigAspect;
            sigDrawX = sigX + 4;
            sigDrawY = sigY + sigHeight / 2 - sigDrawHeight / 2;
          } else {
            sigDrawHeight = sigHeight - 8;
            sigDrawWidth = sigDrawHeight * sigAspect;
            sigDrawX = sigX + sigWidth / 2 - sigDrawWidth / 2;
            sigDrawY = sigY + 4;
          }

          page.drawImage(signatureImage, {
            x: sigDrawX,
            y: sigDrawY,
            width: sigDrawWidth,
            height: sigDrawHeight,
          });

          // Signature details below box
          const signerName = timelog.customerSignerName || 'Unknown';
          page.drawText(signerName, {
            x: sigX,
            y: sigY - 12,
            size: 8,
            font: bold,
            color: darkGray,
          });

          const signedDate = timelog.signedAt?.toDate ? timelog.signedAt.toDate() : new Date(timelog.signedAt);
          page.drawText(signedDate.toLocaleString(), {
            x: sigX,
            y: sigY - 22,
            size: 7,
            font: regular,
            color: mediumGray,
          });
        }
      } catch (error) {
        console.error('Failed to embed signature:', error);
        page.drawText('Signature unavailable', {
          x: sigX,
          y: sigY + 20,
          size: 8,
          font: regular,
          color: this.PDFLib.rgb(0.7, 0, 0),
        });
      }
    }

    // === FOOTER ===
    page.drawLine({
      start: { x: margin, y: 35 },
      end: { x: width - margin, y: 35 },
      thickness: 0.5,
      color: borderGray,
    });

    page.drawText('EasyLog Time Tracking System', {
      x: margin,
      y: 22,
      size: 8,
      font: bold,
      color: mediumGray,
    });

    const footerRight = `Generated ${new Date().toLocaleString()}`;
    page.drawText(footerRight, {
      x: width - margin - regular.widthOfTextAtSize(footerRight, 7),
      y: 22,
      size: 7,
      font: regular,
      color: lightGray,
    });

    return await pdfDoc.save();
  }

  /**
   * Generate multiple job cards as a ZIP file
   * @param {Array<Object>} jobsData - Array of {timelog, customer, photoUrls}
   * @param {Function} onProgress - Callback for progress updates (current, total)
   * @returns {Promise<Blob>} ZIP file blob
   */
  async generateBatchZip(jobsData, onProgress = null) {
    if (!window.JSZip) {
      throw new Error('JSZip not loaded. Please include JSZip script in HTML.');
    }

    const zip = new JSZip();
    const total = jobsData.length;

    for (let i = 0; i < jobsData.length; i++) {
      const { timelog, customer, photoUrls } = jobsData[i];

      if (onProgress) {
        onProgress(i + 1, total);
      }

      try {
        const pdfBytes = await this.generateJobCard(timelog, customer, photoUrls);
        const filename = this._generateFilename(timelog);
        zip.file(filename, pdfBytes);
      } catch (error) {
        console.error(`Failed to generate PDF for job ${timelog.id}:`, error);
        // Continue with other jobs
      }
    }

    return await zip.generateAsync({
      type: 'blob',
      compression: 'DEFLATE',
      compressionOptions: { level: 6 }
    });
  }

  /**
   * Generate multiple job cards as a single multi-page PDF
   * @param {Array<Object>} jobsData - Array of {timelog, customer, photoUrls}
   * @param {Function} onProgress - Callback for progress updates (current, total)
   * @returns {Promise<Uint8Array>} PDF bytes
   */
  async generateBatchMultiPage(jobsData, onProgress = null) {
    const mainPdfDoc = await this.PDFLib.PDFDocument.create();
    const total = jobsData.length;

    for (let i = 0; i < jobsData.length; i++) {
      const { timelog, customer, photoUrls } = jobsData[i];

      if (onProgress) {
        onProgress(i + 1, total);
      }

      try {
        // Generate individual job card
        const pdfBytes = await this.generateJobCard(timelog, customer, photoUrls);

        // Load it as a PDF document
        const jobPdfDoc = await this.PDFLib.PDFDocument.load(pdfBytes);

        // Copy all pages to main document
        const copiedPages = await mainPdfDoc.copyPages(jobPdfDoc, jobPdfDoc.getPageIndices());
        copiedPages.forEach(page => mainPdfDoc.addPage(page));
      } catch (error) {
        console.error(`Failed to add job ${timelog.id} to PDF:`, error);
        // Continue with other jobs
      }
    }

    return await mainPdfDoc.save();
  }

  /**
   * Download PDF bytes as a file
   * @param {Uint8Array|Blob} data - PDF bytes or blob
   * @param {string} filename - Filename for download
   */
  downloadPDF(data, filename) {
    const blob = data instanceof Blob ? data : new Blob([data], { type: 'application/pdf' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  /**
   * Generate filename for a job card
   * @private
   */
  _generateFilename(timelog) {
    const date = timelog.timeStarted?.toDate ? timelog.timeStarted.toDate() : new Date(timelog.timeStarted);
    const dateStr = date.toISOString().split('T')[0];
    const customerName = timelog.customerName.replace(/[^a-z0-9]/gi, '_');
    const jobId = (timelog.localId || timelog.id).substring(0, 8);
    return `JobCard_${dateStr}_${customerName}_${jobId}.pdf`;
  }

  /**
   * Draw compact key-value pair for two-column layout
   * @private
   */
  _drawCompactKeyValue(page, key, value, x, y, maxWidth, regularFont, boldFont) {
    const labelColor = this.PDFLib.rgb(0.5, 0.5, 0.5);
    const valueColor = this.PDFLib.rgb(0.2, 0.2, 0.2);

    page.drawText(key + ':', {
      x: x,
      y: y,
      size: 8,
      font: regularFont,
      color: labelColor,
    });

    const valueStr = String(value || '-');
    const maxValueLength = Math.floor(maxWidth / 4); // Rough estimate
    const displayValue = valueStr.length > maxValueLength
      ? valueStr.substring(0, maxValueLength - 3) + '...'
      : valueStr;

    page.drawText(displayValue, {
      x: x + 50,
      y: y,
      size: 9,
      font: boldFont,
      color: valueColor,
    });
  }

  /**
   * Wrap text to fit within width
   * @private
   */
  _wrapText(text, maxChars) {
    const words = text.split(' ');
    const lines = [];
    let currentLine = '';

    for (const word of words) {
      if ((currentLine + word).length > maxChars) {
        if (currentLine) {
          lines.push(currentLine.trim());
        }
        currentLine = word + ' ';
      } else {
        currentLine += word + ' ';
      }
    }

    if (currentLine) {
      lines.push(currentLine.trim());
    }

    return lines;
  }

  /**
   * Fetch image bytes from URL
   * Note: Requires CORS to be configured on the GCS bucket
   * @private
   */
  async _fetchImageBytes(url) {
    try {
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      return await response.arrayBuffer();
    } catch (fetchError) {
      console.error('Failed to fetch image:', fetchError);
      throw new Error(`Failed to fetch image from ${url}: ${fetchError.message}`);
    }
  }
}

// Export for module usage
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { JobCardPDFGenerator };
}
