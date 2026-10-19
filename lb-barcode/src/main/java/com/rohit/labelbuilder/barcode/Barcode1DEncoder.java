package com.rohit.labelbuilder.barcode;

import com.rohit.labelbuilder.model.style.CheckDigitMode;
import com.rohit.labelbuilder.model.style.HriPosition;
import com.rohit.labelbuilder.model.style.Symbology;
import org.krysalis.barcode4j.ChecksumMode;
import org.krysalis.barcode4j.HumanReadablePlacement;
import org.krysalis.barcode4j.impl.AbstractBarcodeBean;
import org.krysalis.barcode4j.impl.codabar.CodabarBean;
import org.krysalis.barcode4j.impl.code128.Code128Bean;
import org.krysalis.barcode4j.impl.code128.EAN128Bean;
import org.krysalis.barcode4j.impl.code39.Code39Bean;
import org.krysalis.barcode4j.impl.int2of5.ITF14Bean;
import org.krysalis.barcode4j.impl.int2of5.Interleaved2Of5Bean;
import org.krysalis.barcode4j.impl.upcean.EAN13Bean;
import org.krysalis.barcode4j.impl.upcean.EAN8Bean;
import org.krysalis.barcode4j.impl.upcean.UPCABean;
import org.krysalis.barcode4j.impl.upcean.UPCEANBean;
import org.krysalis.barcode4j.impl.upcean.UPCEBean;

/**
 * Encodes one-dimensional symbologies with Barcode4J (Phase 10a), chosen over ZXing for 1D because it
 * exposes a true X-dimension and quiet-zone control rather than only a target pixel size
 * (architecture §11) — the difference between a barcode that scans and one that merely looks right.
 *
 * <p>Output is millimetre geometry; see {@link BarcodeGeometry}. Two-dimensional symbologies are
 * ZXing's job and are rejected here.
 */
public final class Barcode1DEncoder {

    private Barcode1DEncoder() {}

    /**
     * Encode a value.
     *
     * @throws BarcodeException if the symbology is unsupported or the value cannot be encoded by it
     */
    public static BarcodeGeometry encode(BarcodeRequest request) {
        if (request.symbology().isTwoDimensional()) {
            throw new BarcodeException(request.symbology() + " is a 2D symbology — use the 2D encoder, not the 1D one");
        }
        AbstractBarcodeBean bean = beanFor(request.symbology());
        configure(bean, request);

        GeometryCanvasProvider canvas = new GeometryCanvasProvider();
        try {
            bean.generateBarcode(canvas, request.value());
        } catch (BarcodeException e) {
            throw e;
        } catch (RuntimeException e) {
            // Barcode4J reports bad data as IllegalArgumentException; restate it with the context the
            // user needs — which symbology rejected which value, and why.
            throw new BarcodeException(
                    "cannot encode \"%s\" as %s: %s".formatted(request.value(), request.symbology(), e.getMessage()),
                    e);
        }
        return canvas.geometry();
    }

    /** True when {@link #encode} can handle this symbology in this build. */
    public static boolean supports(Symbology symbology) {
        if (symbology.isTwoDimensional()) {
            return false;
        }
        try {
            beanFor(symbology);
            return true;
        } catch (BarcodeException e) {
            return false;
        }
    }

    private static AbstractBarcodeBean beanFor(Symbology symbology) {
        return switch (symbology) {
            case CODE_128 -> new Code128Bean();
            case GS1_128 -> new EAN128Bean();
            case CODE_39 -> new Code39Bean();
            case EAN_13 -> new EAN13Bean();
            case EAN_8 -> new EAN8Bean();
            case UPC_A -> new UPCABean();
            case UPC_E -> new UPCEBean();
            case ITF_14 -> new ITF14Bean();
            case INTERLEAVED_2_OF_5 -> new Interleaved2Of5Bean();
            case CODABAR -> new CodabarBean();
            // Declared by the format, but Barcode4J 2.1 ships no MSI implementation. Fail clearly
            // rather than silently substituting a different symbology.
            case MSI ->
                throw new BarcodeException(
                        "MSI is not supported by this build (Barcode4J 2.1 provides no MSI encoder)");
            default -> throw new BarcodeException("not a 1D symbology: " + symbology);
        };
    }

    private static void configure(AbstractBarcodeBean bean, BarcodeRequest request) {
        bean.setModuleWidth(request.moduleWidthMm());
        bean.setBarHeight(request.barHeightMm());
        bean.doQuietZone(request.quietZoneMm() > 0);
        bean.setQuietZone(request.quietZoneMm());
        bean.setMsgPosition(placement(request.hriPosition()));
        if (request.hriPosition() != HriPosition.NONE) {
            bean.setFontName(request.hriFontFamily());
            bean.setFontSize(request.hriFontSizeMm());
        }
        applyChecksum(bean, request.checkDigit());
    }

    /**
     * Apply the check-digit policy where the symbology has one. Code 128 and GS1-128 are absent by
     * design: they always carry their own check character, so there is nothing to choose.
     */
    private static void applyChecksum(AbstractBarcodeBean bean, CheckDigitMode mode) {
        ChecksumMode checksum = checksumMode(mode);
        if (bean instanceof Code39Bean code39) {
            code39.setChecksumMode(checksum);
        } else if (bean instanceof Interleaved2Of5Bean interleaved) {
            interleaved.setChecksumMode(checksum);
        } else if (bean instanceof CodabarBean codabar) {
            codabar.setChecksumMode(checksum);
        } else if (bean instanceof UPCEANBean upcean) {
            upcean.setChecksumMode(checksum);
        }
    }

    private static ChecksumMode checksumMode(CheckDigitMode mode) {
        return switch (mode) {
            case AUTO -> ChecksumMode.CP_AUTO;
            case NONE -> ChecksumMode.CP_IGNORE;
            // "The value already includes one" means verify it, not append another.
            case PROVIDED -> ChecksumMode.CP_CHECK;
        };
    }

    private static HumanReadablePlacement placement(HriPosition position) {
        return switch (position) {
            case ABOVE -> HumanReadablePlacement.HRP_TOP;
            case BELOW -> HumanReadablePlacement.HRP_BOTTOM;
            case NONE -> HumanReadablePlacement.HRP_NONE;
        };
    }
}
