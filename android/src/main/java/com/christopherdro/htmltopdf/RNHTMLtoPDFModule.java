package com.christopherdro.htmltopdf;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableArray;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.nio.charset.Charset;

import com.itextpdf.text.*;
import com.itextpdf.tool.xml.*;
import com.itextpdf.tool.xml.pipeline.*;
import com.itextpdf.tool.xml.Tag;
import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.codec.Base64;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import com.itextpdf.text.FontFactory;
import com.itextpdf.tool.xml.XMLWorkerFontProvider;
import com.itextpdf.tool.xml.parser.XMLParser;
import com.itextpdf.tool.xml.pipeline.css.CSSResolver;
import com.itextpdf.tool.xml.pipeline.css.CssResolverPipeline;
import com.itextpdf.tool.xml.pipeline.html.*;
import com.itextpdf.tool.xml.pipeline.end.*;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipeline;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipelineContext;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.codec.Base64;
import com.itextpdf.tool.xml.html.Tags;
import com.itextpdf.tool.xml.pipeline.html.AbstractImageProvider;


import android.os.Environment;

public class RNHTMLtoPDFModule extends ReactContextBaseJavaModule {


  private Promise promise;
  private final ReactApplicationContext mReactContext;
  private Set<String> customFonts = new HashSet<>();

  XMLWorkerFontProvider fontProvider = new XMLWorkerFontProvider(XMLWorkerFontProvider.DONTLOOKFORFONTS);

  /**
  * Code to get Base64,url or storage from tag o <img/>
  * source : https://developers.itextpdf.com/ja/examples/xml-worker-itext5/html-images
  * Editado por : Michael Vargas - Bucaramanga - Colombia
  */
  class Base64ImageProvider extends AbstractImageProvider {

    @Override
    public Image retrieve(String src) {
      int pos = src.indexOf("base64,");
      try {
        if (src.startsWith("data") && pos > 0) {
          byte[] img = Base64.decode(src.substring(pos + 7));
          return Image.getInstance(img);
        } else {
          return Image.getInstance(src);
        }
      } catch (BadElementException ex) {
        return null;
      } catch (IOException ex) {
        return null;
      }
    }

    @Override
    public String getImageRootPath() {
      return null;
    }
  }
  public RNHTMLtoPDFModule(ReactApplicationContext reactContext) {
    super(reactContext);
    mReactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNHTMLtoPDF";
  }

  @ReactMethod
  public void convert(final ReadableMap options, final Promise promise) {
    try {
      File destinationFile;
      String htmlString = options.hasKey("html") ? options.getString("html") : null;
      if (htmlString == null) return;

      String fileName;
      if (options.hasKey("fileName")) {
        fileName = options.getString("fileName");
      } else {
        fileName = UUID.randomUUID().toString();
      }

      if (options.hasKey("fonts")) {
        if (options.getArray("fonts") != null) {
          final ReadableArray fonts = options.getArray("fonts");
          for (int i = 0; i < fonts.size(); i++) {
            customFonts.add(fonts.getString(i));
          }
        }
      }

      if (options.hasKey("directory") && options.getString("directory").equals("docs")) {
        String state = Environment.getExternalStorageState();
          File path = (Environment.MEDIA_MOUNTED.equals(state)) ?
                  new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOCUMENTS)
                  : new File(mReactContext.getFilesDir(), Environment.DIRECTORY_DOCUMENTS);

        if (!path.exists()) path.mkdir();
        destinationFile = new File(path, fileName + ".pdf");
      } else {
        destinationFile = getTempFile(fileName);
      }

      String filePath = convertToPDF(htmlString, destinationFile);
      String base64 = "";

      if (options.hasKey("base64") && options.getBoolean("base64") == true) {
        base64 = Base64.encodeFromFile(filePath);
      }

      WritableMap resultMap = Arguments.createMap();
      resultMap.putString("filePath", filePath);
      resultMap.putString("base64", base64);

      promise.resolve(resultMap);
    } catch (Exception e) {
      promise.reject(e.getMessage());
    }
  }
  private String convertToPDF(String htmlString, File file) throws Exception {
    try {
      Document document = new Document();
      PdfWriter pdfWriter = PdfWriter.getInstance(document, new FileOutputStream(file));

      FontFactory.setFontImp(fontProvider);
      for (String font : customFonts) {
        fontProvider.register(font);
      }

      document.open();
      document.add(new Chunk("")); // This prevent empty document exception

      CSSResolver cssResolver =
              XMLWorkerHelper.getInstance().getDefaultCssResolver(true);

      HtmlPipelineContext htmlContext = new HtmlPipelineContext(null);
      htmlContext.setTagFactory(Tags.getHtmlTagProcessorFactory());
      htmlContext.setImageProvider(new Base64ImageProvider());
      
      PdfWriterPipeline pdfWriterPipeline = new PdfWriterPipeline(document, pdfWriter);
      HtmlPipeline html = new HtmlPipeline(htmlContext, pdfWriterPipeline);
      CssResolverPipeline css = new CssResolverPipeline(cssResolver, html);

      XMLWorker xmlWorker = new XMLWorker(css, true);
      XMLParser xmlParser = new XMLParser(xmlWorker);
      xmlParser.parse(new ByteArrayInputStream(htmlString.getBytes()));
      document.close();

      String absolutePath = file.getAbsolutePath();
      return absolutePath;
    } catch (Exception e) {
      throw new Exception(e);
    }
  }

  private File getTempFile(String fileName) throws Exception {
    try {
      File outputDir = getReactApplicationContext().getCacheDir();
      File outputFile = File.createTempFile("PDF_" + UUID.randomUUID().toString(), ".pdf", outputDir);

      return outputFile;

    } catch (Exception e) {
      throw new Exception(e);
    }
  }

}