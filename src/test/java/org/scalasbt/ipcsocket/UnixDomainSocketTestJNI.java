package org.scalasbt.ipcsocket;

public class UnixDomainSocketTestJNI extends UnixDomainSocketTest {
  @Override
  boolean useJNI() {
    return true;
  }
}
