package org.anddev.andengine.extension.svg.adt;

import java.util.HashMap;

import org.anddev.andengine.extension.svg.adt.gradient.SVGGradient;
import org.anddev.andengine.extension.svg.adt.gradient.SVGLinearGradient;
import org.anddev.andengine.extension.svg.adt.gradient.SVGRadialGradient;
import org.anddev.andengine.extension.svg.adt.gradient.SVGGradient.Stop;
import org.anddev.andengine.extension.svg.exception.SVGParseException;
import org.anddev.andengine.extension.svg.util.SAXHelper;
import org.anddev.andengine.extension.svg.util.SVGNumberParser;
import org.anddev.andengine.extension.svg.util.SVGTransformParser;
import org.anddev.andengine.extension.svg.util.SVGNumberParser.SVGNumberParserIntegerResult;
import org.anddev.andengine.extension.svg.util.constants.ColorUtils;
import org.xml.sax.Attributes;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;


/**
 * TODO Add ColorMapping - maybe a HashMap<Integer,Integer> and make us of it in
 * parseColor(...). Constructor should take such a ColorMapping object then.
 * 
 * @author Nicolas Gramlich
 * @since 22:01:39 - 23.05.2011
 */
public class SVGPaint {
	// ===========================================================
	// Constants
	// ===========================================================

	// ===========================================================
	// Fields
	// ===========================================================

	private final Paint mPaint = new Paint();

	/** Multi purpose dummy rectangle. */
	private final RectF mRect = new RectF();
	private final RectF mComputedBounds = new RectF(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);

	private final HashMap<String, Shader> mSVGGradientShaderMap = new HashMap<String, Shader>();
	private final HashMap<String, SVGGradient> mSVGGradientMap = new HashMap<String, SVGGradient>();
	private final ISVGColorMapper mSVGColorMapper;

	// ===========================================================
	// Constructors
	// ===========================================================

	public SVGPaint(final ISVGColorMapper pSVGColorMapper) {
		this.mSVGColorMapper = pSVGColorMapper;
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	public Paint getPaint() {
		return this.mPaint;
	}

	public RectF getComputedBounds() {
		return this.mComputedBounds;
	}

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================

	// ===========================================================
	// Methods
	// ===========================================================

	public void resetPaint(final Style pStyle) {
		this.mPaint.reset();
		this.mPaint.setAntiAlias(true); // TODO AntiAliasing could be made optional through some SVGOptions object.
		this.mPaint.setStyle(pStyle);
	}

	public boolean setFill(final SVGProperties pSVGProperties) {
		if(this.isDisplayNone(pSVGProperties) || this.isFillNone(pSVGProperties)) {
			return false;
		}

		this.resetPaint(Paint.Style.FILL);

		final String fillProperty = pSVGProperties.getStringProperty("fill");
		if(fillProperty == null) {
			if(pSVGProperties.getStringProperty("stroke") == null) {
				/* Default is black fill. */
				this.mPaint.setColor(0xFF000000); // TODO Respect color mapping?
				return true;
			} else {
				return false;
			}
		} else {
			return this.setPaintProperties(pSVGProperties, true);
		}
	}

	public boolean setStroke(final SVGProperties pSVGProperties) {
		if(this.isDisplayNone(pSVGProperties) || this.isStrokeNone(pSVGProperties)) {
			return false;
		}

		this.resetPaint(Paint.Style.STROKE);

		return this.setPaintProperties(pSVGProperties, false);
	}

	private boolean isDisplayNone(final SVGProperties pSVGProperties) {
		return "none".equals(pSVGProperties.getStringProperty("display"));
	}

	private boolean isFillNone(final SVGProperties pSVGProperties) {
		return "none".equals(pSVGProperties.getStringProperty("fill"));
	}

	private boolean isStrokeNone(final SVGProperties pSVGProperties) {
		return "none".equals(pSVGProperties.getStringProperty("stroke"));
	}

	public boolean setPaintProperties(final SVGProperties pSVGProperties, final boolean pModeFill) {
		if(this.applyColorProperties(pSVGProperties, pModeFill)) {
			if(pModeFill) {
				return this.applyFillProperties(pSVGProperties);
			} else {
				return this.applyStrokeProperties(pSVGProperties);
			}
		} else {
			return false;
		}
	}

	private boolean applyColorProperties(final SVGProperties pSVGProperties, final boolean pModeFill) {
		final String colorProperty = pSVGProperties.getStringProperty(pModeFill ? "fill" : "stroke");
		if(colorProperty == null) {
			return false;
		}

		if(colorProperty.startsWith("url(#")) {
			final String id = colorProperty.substring("url(#".length(), colorProperty.length() - 1);

			Shader gradientShader = this.mSVGGradientShaderMap.get(id);
			if(gradientShader == null) {
				final SVGGradient svgGradient = this.mSVGGradientMap.get(id);
				if(svgGradient == null) {
					throw new SVGParseException("No gradient found for id: '" + id + "'.");
				} else {
					this.registerGradientShader(svgGradient);
					gradientShader = this.mSVGGradientShaderMap.get(id);
				}
			}
			this.mPaint.setShader(gradientShader);
			return true;
		} else {
			final Integer color = this.parseColor(colorProperty);
			if(color != null) {
				this.applyColor(pSVGProperties, color, pModeFill);
				return true;
			} else {
				return false;
			}
		}
	}

	private boolean applyFillProperties(final SVGProperties pSVGProperties) {
		return true;
	}

	private boolean applyStrokeProperties(final SVGProperties pSVGProperties) {
		final Float width = pSVGProperties.getFloatProperty("stroke-width");
		if (width != null) {
			this.mPaint.setStrokeWidth(width);
		}
		final String linecap = pSVGProperties.getStringProperty("stroke-linecap");
		if ("round".equals(linecap)) {
			this.mPaint.setStrokeCap(Paint.Cap.ROUND);
		} else if ("square".equals(linecap)) {
			this.mPaint.setStrokeCap(Paint.Cap.SQUARE);
		} else if ("butt".equals(linecap)) {
			this.mPaint.setStrokeCap(Paint.Cap.BUTT);
		}
		final String linejoin = pSVGProperties.getStringProperty("stroke-linejoin");
		if ("miter".equals(linejoin)) {
			this.mPaint.setStrokeJoin(Paint.Join.MITER);
		} else if ("round".equals(linejoin)) {
			this.mPaint.setStrokeJoin(Paint.Join.ROUND);
		} else if ("bevel".equals(linejoin)) {
			this.mPaint.setStrokeJoin(Paint.Join.BEVEL);
		}
		return true;
	}

	private void applyColor(final SVGProperties pSVGProperties, final Integer pColor, final boolean pModeFill) {
		final int c = (ColorUtils.COLOR_MASK_32BIT_ARGB_RGB & pColor) | ColorUtils.COLOR_MASK_32BIT_ARGB_ALPHA;
		this.mPaint.setColor(c);
		this.mPaint.setAlpha(SVGPaint.parseAlpha(pSVGProperties, pModeFill));
	}

	private static int parseAlpha(final SVGProperties pSVGProperties, final boolean pModeFill) {
		Float opacity = pSVGProperties.getFloatProperty("opacity");
		if(opacity == null) {
			opacity = pSVGProperties.getFloatProperty(pModeFill ? "fill-opacity" : "stroke-opacity");
		}
		if(opacity == null) {
			return 255;
		} else {
			return (int) (255 * opacity);
		}
	}

	private void registerGradientShader(final SVGGradient pSVGGradient) {
		final String gradientID = pSVGGradient.getID();
		if(this.hasGradientShader(pSVGGradient)) {
			/* Nothing to do, as Shader was already created. */
		} else if(pSVGGradient.hasXLink()) {
			final SVGGradient parent = this.mSVGGradientMap.get(pSVGGradient.getXLink());
			if(parent == null) {
				throw new SVGParseException("Could not resolve xlink: '" + pSVGGradient.getXLink() + "' of gradient: '" + gradientID + "'.");
			} else {
				if(parent.hasXLink() && !this.hasGradientShader(parent)) {
					this.registerGradientShader(parent);
				}
				final SVGGradient svgGradient = SVGGradient.deriveChild(parent, pSVGGradient);

				this.mSVGGradientMap.put(gradientID, svgGradient);
				this.mSVGGradientShaderMap.put(gradientID, svgGradient.createShader());
			}
		} else {
			this.mSVGGradientShaderMap.put(gradientID, pSVGGradient.createShader());
		}
	}

	private boolean hasGradientShader(final SVGGradient pSVGGradient) {
		return this.mSVGGradientShaderMap.containsKey(pSVGGradient.getID());
	}

	public void clearGradientShaders() {
		this.mSVGGradientShaderMap.clear();
	}

	public void ensureComputedBoundsInclude(final float pX, final float pY) {
		if (pX < this.mComputedBounds.left) {
			this.mComputedBounds.left = pX;
		}
		if (pX > this.mComputedBounds.right) {
			this.mComputedBounds.right = pX;
		}
		if (pY < this.mComputedBounds.top) {
			this.mComputedBounds.top = pY;
		}
		if (pY > this.mComputedBounds.bottom) {
			this.mComputedBounds.bottom = pY;
		}
	}

	public void ensureComputedBoundsInclude(final float pX, final float pY, final float pWidth, final float pHeight) {
		this.ensureComputedBoundsInclude(pX, pY);
		this.ensureComputedBoundsInclude(pX + pWidth, pY + pHeight);
	}

	public void ensureComputedBoundsInclude(final Path pPath) {
		pPath.computeBounds(this.mRect, false);
		this.ensureComputedBoundsInclude(this.mRect.left, this.mRect.top);
		this.ensureComputedBoundsInclude(this.mRect.right, this.mRect.bottom);
	}

	// ===========================================================
	// Methods for Color-Parsing
	// ===========================================================

	private Integer parseColor(final String pString, final Integer pDefault) {
		final Integer color = this.parseColor(pString);
		if(color == null) {
			return this.applySVGColorMapper(pDefault);
		} else {
			return color;
		}
	}

	private Integer parseColor(final String pString) {
		/*
		 * TODO Test if explicit pattern matching is faster:
		 * /^rgb\((\d{1,3}),\s*(\d{1,3}),\s*(\d{1,3})\)$/
		 * /^(\w{2})(\w{2})(\w{2})$/
		 * /^(\w{1})(\w{1})(\w{1})$/
		 */

		final Integer parsedColor;
		if(pString == null) {
			parsedColor = null;
		} else if(pString.startsWith("#")) {
			final String hexColorString = pString.substring(1).trim();
			if(hexColorString.length() == 3) {
				final int parsedInt = Integer.parseInt(hexColorString, 16);
				final int red = (parsedInt & ColorUtils.COLOR_MASK_12BIT_RGB_R) >> 8;
				final int green = (parsedInt & ColorUtils.COLOR_MASK_12BIT_RGB_G) >> 4;
				final int blue = (parsedInt & ColorUtils.COLOR_MASK_12BIT_RGB_B) >> 0;
				/* Generate color, duplicating the bits, so that i.e.: #F46 gets #FFAA66. */
				parsedColor = Color.argb(0, (red << 4) | red, (green << 4) | green, (blue << 4) | blue);
			} else if(hexColorString.length() == 6) {
				parsedColor = Integer.parseInt(hexColorString, 16);
			} else {
				parsedColor = null;
			}
		} else if(pString.startsWith("rgb(")) {
			final SVGNumberParserIntegerResult svgNumberParserIntegerResult = SVGNumberParser.parseInts(pString.substring("rgb(".length(), pString.indexOf(')')));
			if(svgNumberParserIntegerResult.getNumberCount() == 3) {
				parsedColor = Color.argb(0, svgNumberParserIntegerResult.getNumber(0), svgNumberParserIntegerResult.getNumber(1), svgNumberParserIntegerResult.getNumber(2));
			} else {
				parsedColor = null;
			}
		} else {
			final Integer colorByName = ColorUtils.getColorByName(pString.trim());
			if(colorByName != null) {
				parsedColor = colorByName;
			} else {
				parsedColor = Integer.parseInt(pString, 16);
			}
		}
		return this.applySVGColorMapper(parsedColor);
	}

	private Integer applySVGColorMapper(final Integer parsedColor) {
		if(this.mSVGColorMapper == null) {
			return parsedColor;
		} else {
			return this.mSVGColorMapper.mapColor(parsedColor);
		}
	}

	// ===========================================================
	// Methods for Gradient-Parsing
	// ===========================================================

	public SVGGradient registerGradient(final Attributes pAttributes, final boolean pLinear) {
		final String id = SAXHelper.getStringAttribute(pAttributes, "id");
		if(id == null) {
			return null;
		}
		final Matrix matrix = SVGTransformParser.parseTransform(SAXHelper.getStringAttribute(pAttributes, "gradientTransform"));
		String xlink = SAXHelper.getStringAttribute(pAttributes, "href");
		if(xlink != null) {
			if(xlink.startsWith("#")) {
				xlink = xlink.substring(1);
			}
		}
		final SVGGradient svgGradient;
		if(pLinear) {
			final float x1 = SAXHelper.getFloatAttribute(pAttributes, "x1", 0f);
			final float x2 = SAXHelper.getFloatAttribute(pAttributes, "x2", 0f);
			final float y1 = SAXHelper.getFloatAttribute(pAttributes, "y1", 0f);
			final float y2 = SAXHelper.getFloatAttribute(pAttributes, "y2", 0f);
			svgGradient = new SVGLinearGradient(id, x1, x2, y1, y2, matrix, xlink);
		} else {
			final float centerX = SAXHelper.getFloatAttribute(pAttributes, "cx", 0f);
			final float centerY = SAXHelper.getFloatAttribute(pAttributes, "cy", 0f);
			final float radius = SAXHelper.getFloatAttribute(pAttributes, "r", 0f);
			svgGradient = new SVGRadialGradient(id, centerX, centerY, radius, matrix, xlink);
		}
		this.mSVGGradientMap.put(id, svgGradient);
		return svgGradient;
	}

	public Stop parseGradientStop(final SVGProperties pSVGProperties) {
		final float offset = pSVGProperties.getFloatProperty("offset", 0f);
		final String stopColor = pSVGProperties.getStringProperty("stop-color");
		final int rgb = this.parseColor(stopColor.trim(), Color.BLACK);
		final int alpha = this.parseGradientStopAlpha(pSVGProperties);
		return new Stop(offset, alpha | rgb);
	}

	private int parseGradientStopAlpha(final SVGProperties pSVGProperties) {
		final String opacityStyle = pSVGProperties.getStringProperty("stop-opacity");
		if(opacityStyle != null) {
			final float alpha = Float.parseFloat(opacityStyle);
			final int alphaInt = Math.round(255 * alpha);
			return (alphaInt << 24);
		} else {
			return ColorUtils.COLOR_MASK_32BIT_ARGB_ALPHA;
		}
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
}