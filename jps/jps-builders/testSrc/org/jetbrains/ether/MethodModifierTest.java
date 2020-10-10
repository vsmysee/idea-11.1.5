package org.jetbrains.ether;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 04.10.11
 * Time: 16:06
 * To change this template use File | Settings | File Templates.
 */
public class MethodModifierTest extends IncrementalTestCase {
  public MethodModifierTest() throws Exception {
    super("methodModifiers");
  }

  public void testDecConstructorAccess() throws Exception {
    doTest();
  }

  public void testIncAccess() throws Exception {
    doTest();
  }

  public void testSetAbstract() throws Exception {
    doTest();
  }

  public void testSetFinal() throws Exception {
    doTest();
  }

  public void testSetPrivate() throws Exception {
    doTest();
  }

  public void testSetProtected() throws Exception {
    doTest();
  }


  public void testUnsetFinal() throws Exception {
    doTest();
  }

  public void testUnsetStatic() throws Exception {
    doTest();
  }

  public void testSetStatic() throws Exception {
    doTest();
  }
}
