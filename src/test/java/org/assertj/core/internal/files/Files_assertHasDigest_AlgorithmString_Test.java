/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Copyright 2012-2021 the original author or authors.
 */
package org.assertj.core.internal.files;

import static java.nio.file.Files.readAllBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.error.ShouldBeFile.shouldBeFile;
import static org.assertj.core.error.ShouldBeReadable.shouldBeReadable;
import static org.assertj.core.error.ShouldExist.shouldExist;
import static org.assertj.core.error.ShouldHaveDigest.shouldHaveDigest;
import static org.assertj.core.internal.Digests.toHex;
import static org.assertj.core.test.TestData.someInfo;
import static org.assertj.core.util.AssertionsUtil.expectAssertionError;
import static org.assertj.core.util.FailureMessages.actualIsNull;
import static org.assertj.core.util.Files.newFile;
import static org.assertj.core.util.Files.newFolder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.assertj.core.api.AssertionInfo;
import org.assertj.core.internal.DigestDiff;
import org.assertj.core.internal.Digests;
import org.assertj.core.internal.Files;
import org.assertj.core.internal.FilesBaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for <code>{@link Files#assertHasDigest(AssertionInfo, File, MessageDigest, String)}</code>
 *
 * @author Valeriy Vyrva
 */
class Files_assertHasDigest_AlgorithmString_Test extends FilesBaseTest {
  private final String algorithm = "MD5";
  private final String expected = "";

  @Test
  void should_fail_if_actual_is_null() {
    AssertionInfo info = someInfo();
    assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> files.assertHasDigest(info, null, algorithm, expected))
                                                   .withMessage(actualIsNull());
  }

  @Test
  void should_fail_with_should_exist_error_if_actual_does_not_exist() {
    // GIVEN
    File actual = new File("xyz");
    // WHEN
    catchThrowable(() -> files.assertHasDigest(INFO, actual, algorithm, expected));
    // THEN
    verify(failures).failure(INFO, shouldExist(actual));
  }

  @Test
  void should_fail_if_actual_exists_but_is_not_file() {
    // GIVEN
    File actual = newFolder(tempDir.getAbsolutePath() + "/tmp");
    // WHEN
    catchThrowable(() -> files.assertHasDigest(INFO, actual, algorithm, expected));
    // THEN
    verify(failures).failure(INFO, shouldBeFile(actual));
  }

  @Test
  void should_fail_if_actual_exists_but_is_not_readable() {
    // GIVEN
    File actual = newFile(tempDir.getAbsolutePath() + "/Test.java");
    actual.setReadable(false);
    // WHEN
    catchThrowable(() -> files.assertHasDigest(INFO, actual, algorithm, expected));
    // THEN
    verify(failures).failure(INFO, shouldBeReadable(actual));
  }

  @Test
  void should_throw_error_if_digest_is_null() {
    assertThatNullPointerException().isThrownBy(() -> files.assertHasDigest(INFO, null, (MessageDigest) null, expected))
                                    .withMessage("The message digest algorithm should not be null");
  }

  @Test
  void should_throw_error_if_expected_is_null() {
    assertThatNullPointerException().isThrownBy(() -> files.assertHasDigest(INFO, null, algorithm, (byte[]) null))
                                    .withMessage("The binary representation of digest to compare to should not be null");
  }

  @Test
  void should_throw_error_wrapping_caught_IOException() throws IOException {
    // GIVEN
    File actual = newFile(tempDir.getAbsolutePath() + "/tmp.txt");
    IOException cause = new IOException();
    given(nioFilesWrapper.newInputStream(any())).willThrow(cause);
    // WHEN
    Throwable error = catchThrowable(() -> files.assertHasDigest(INFO, actual, algorithm, expected));
    // THEN
    assertThat(error).isInstanceOf(UncheckedIOException.class)
                     .hasCause(cause);
  }

  @Test
  void should_throw_error_wrapping_caught_NoSuchAlgorithmException() {
    // GIVEN
    String unknownDigestAlgorithm = "UnknownDigestAlgorithm";
    // WHEN
    Throwable error = catchThrowable(() -> files.assertHasDigest(INFO, actual, unknownDigestAlgorithm, expected));
    // THEN
    assertThat(error).isInstanceOf(IllegalStateException.class)
                     .hasMessage("Unable to find digest implementation for: <UnknownDigestAlgorithm>");
  }

  @Test
  void should_fail_if_actual_does_not_have_expected_digest() throws IOException, NoSuchAlgorithmException {
    // GIVEN
    String algorithm = "MD5";
    byte[] modifiedData = "Bad Content".getBytes();
    File actual = newFile(tempDir.getAbsolutePath() + "/tmp.txt");
    try (FileOutputStream myWriter = new FileOutputStream(actual)) {
      myWriter.write(modifiedData, 0, modifiedData.length);
    }

    byte[] actualData = "Content".getBytes();
    MessageDigest digest = MessageDigest.getInstance("MD5");
    String expected = toHex(digest.digest(actualData));
    DigestDiff digestDiff = new DigestDiff(toHex(digest.digest(readAllBytes(actual.toPath()))), expected, digest);
    // WHEN
    AssertionError error = expectAssertionError(() -> unMockedFiles.assertHasDigest(INFO, actual, algorithm, expected));
    // THEN
    then(error).hasMessage(shouldHaveDigest(actual, digestDiff).create());
  }

  @Test
  void should_pass_if_actual_has_expected_digest() throws Exception {
    // GIVEN
    String algorithm = "MD5";
    byte[] data = "Content".getBytes();

    File actual = newFile(tempDir.getAbsolutePath() + "/tmp.txt");
    try (FileOutputStream myWriter = new FileOutputStream(actual)) {
      myWriter.write(data, 0, data.length);
    }
    String expected = toHex(MessageDigest.getInstance(algorithm).digest(data));
    // WHEN/THEN
    unMockedFiles.assertHasDigest(INFO, actual, algorithm, Digests.fromHex(expected));
  }

}
