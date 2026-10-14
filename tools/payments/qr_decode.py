#!/usr/bin/env python3
"""Prints the text of the QR code in an image (PGM/PNG), for the payment bot: an outside decoder (OpenCV), so the code
the game draws is checked by something other than the code that drew it. Needs opencv-python-headless.

  python3 tools/payments/qr_decode.py image.pgm
"""
import sys

import cv2

img = cv2.imread(sys.argv[1], cv2.IMREAD_GRAYSCALE)
if img is None:
    sys.exit("cannot read " + sys.argv[1])
text, _, _ = cv2.QRCodeDetector().detectAndDecode(img)
print(text)
