package org.jetbrains.jps.javac;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import org.jetbrains.jps.client.ProtobufResponseHandler;

import javax.tools.*;
import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.Locale;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12
 */
public class JavacServerResponseHandler implements ProtobufResponseHandler{
  private final DiagnosticOutputConsumer myDiagnosticSink;
  private final OutputFileConsumer myOutputSink;
  private volatile boolean myTerminatedSuccessfully;

  public JavacServerResponseHandler(DiagnosticOutputConsumer diagnosticSink, OutputFileConsumer outputSink) {
    myDiagnosticSink = diagnosticSink;
    myOutputSink = outputSink;
  }

  public boolean handleMessage(MessageLite message) throws Exception {
    final JavacRemoteProto.Message msg = (JavacRemoteProto.Message)message;
    final JavacRemoteProto.Message.Type messageType = msg.getMessageType();

    if (messageType == JavacRemoteProto.Message.Type.RESPONSE) {
      final JavacRemoteProto.Message.Response response = msg.getResponse();
      final JavacRemoteProto.Message.Response.Type responseType = response.getResponseType();

      if (responseType == JavacRemoteProto.Message.Response.Type.BUILD_MESSAGE) {
        final JavacRemoteProto.Message.Response.CompileMessage compileMessage = response.getCompileMessage();
        final JavacRemoteProto.Message.Response.CompileMessage.Kind messageKind = compileMessage.getKind();

        if (messageKind == JavacRemoteProto.Message.Response.CompileMessage.Kind.STD_OUT) {
          myDiagnosticSink.outputLineAvailable(compileMessage.getText());
        }
        else {
          final String sourceUri = compileMessage.getSourceUri();
          final JavaFileObject srcFileObject = sourceUri != null? new DummyJavaFileObject(URI.create(sourceUri)) : null;
          myDiagnosticSink.report(new DummyDiagnostic(convertKind(messageKind), srcFileObject, compileMessage));
        }

        return false;
      }

      if (responseType == JavacRemoteProto.Message.Response.Type.OUTPUT_OBJECT) {
        final JavacRemoteProto.Message.Response.OutputObject outputObject = response.getOutputObject();
        final JavacRemoteProto.Message.Response.OutputObject.Kind kind = outputObject.getKind();

        final String outputRoot = outputObject.getOutputRoot();
        final File outputRootFile = outputRoot != null? new File(outputRoot) : null;

        final OutputFileObject.Content fileObjectContent;
        final ByteString content = outputObject.getContent();
        if (content != null) {
          final byte[] bytes = content.toByteArray();
          fileObjectContent = new OutputFileObject.Content(bytes, 0, bytes.length);
        }
        else {
          fileObjectContent = null;
        }

        final String sourceUri = outputObject.getSourceUri();
        final URI srcUri = sourceUri != null? URI.create(sourceUri) : null;
        final OutputFileObject fileObject = new OutputFileObject(
          null,
          outputRootFile,
          outputObject.getRelativePath(),
          new File(outputObject.getFilePath()),
          convertKind(kind),
          outputObject.getClassName(),
          srcUri,
          fileObjectContent
        );

        myOutputSink.save(fileObject);
        return false;
      }

      if (responseType == JavacRemoteProto.Message.Response.Type.CLASS_DATA) {
        final JavacRemoteProto.Message.Response.ClassData data = response.getClassData();
        final String className = data.getClassName();
        final Collection<String> imports = data.getImportStatementList();
        final Collection<String> staticImports = data.getStaticImportList();
        myDiagnosticSink.registerImports(className, imports, staticImports);
        return false;
      }

      if (responseType == JavacRemoteProto.Message.Response.Type.BUILD_COMPLETED) {
        myTerminatedSuccessfully = response.getCompletionStatus();
        return true;
      }

      if (responseType == JavacRemoteProto.Message.Response.Type.REQUEST_ACK) {
        return true;
      }

      throw new Exception("Unsupported response type: " + responseType.name());
    }

    if (messageType == JavacRemoteProto.Message.Type.FAILURE) {
      final JavacRemoteProto.Message.Failure failure = msg.getFailure();
      myDiagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, failure.getStacktrace()));
      return true;
    }

    throw new Exception("Unsupported message type: " + messageType.name());
  }

  public boolean isTerminatedSuccessfully() {
    return myTerminatedSuccessfully;
  }

  private static Diagnostic.Kind convertKind(JavacRemoteProto.Message.Response.CompileMessage.Kind kind) {
    switch (kind) {
      case ERROR: return Diagnostic.Kind.ERROR;
      case WARNING: return Diagnostic.Kind.WARNING;
      case MANDATORY_WARNING: return Diagnostic.Kind.MANDATORY_WARNING;
      case NOTE: return Diagnostic.Kind.NOTE;
      default : return Diagnostic.Kind.OTHER;
    }
  }

  private static OutputFileObject.Kind convertKind(JavacRemoteProto.Message.Response.OutputObject.Kind kind) {
    switch (kind) {
      case CLASS: return JavaFileObject.Kind.CLASS;
      case HTML: return JavaFileObject.Kind.HTML;
      case SOURCE: return JavaFileObject.Kind.SOURCE;
      default : return JavaFileObject.Kind.OTHER;
    }
  }

  public void sessionTerminated() {
  }

  private static class DummyDiagnostic implements Diagnostic<JavaFileObject> {

    private final Kind myMessageKind;
    private final JavaFileObject mySrcFileObject;
    private final JavacRemoteProto.Message.Response.CompileMessage myCompileMessage;

    public DummyDiagnostic(final Kind messageKind, JavaFileObject srcFileObject, JavacRemoteProto.Message.Response.CompileMessage compileMessage) {
      myMessageKind = messageKind;
      mySrcFileObject = srcFileObject;
      myCompileMessage = compileMessage;
    }

    public Kind getKind() {
      return myMessageKind;
    }

    public JavaFileObject getSource() {
      return mySrcFileObject;
    }

    public long getPosition() {
      return myCompileMessage.getProblemLocationOffset();
    }

    public long getStartPosition() {
      return myCompileMessage.getProblemBeginOffset();
    }

    public long getEndPosition() {
      return myCompileMessage.getProblemEndOffset();
    }

    public long getLineNumber() {
      return myCompileMessage.getLine();
    }

    public long getColumnNumber() {
      return myCompileMessage.getColumn();
    }

    public String getCode() {
      return null;
    }

    public String getMessage(Locale locale) {
      return myCompileMessage.getText();
    }
  }
}
