/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara;

import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * A revision of {@link Origin}.
 *
 * <p>For example, in Git it would be a commit SHA-1.
 */
public interface Revision {

  /**
   * Reads the timestamp of this revision from the repository, or {@code null} if this repo type
   * does not support it. This is the {@link Instant} from the UNIX epoch when the revision was
   * submitted to the source repository.
   */
  @Nullable
  Instant readTimestamp() throws RepoException;

  /**
   * String representation of the revision that can be parsed by {@link Origin#resolve(String)}.
   *
   * <p> Unlike {@link #toString()} method, this method is guaranteed to be stable.
   */
  String asString();

  /**
   * Label name to be used in when creating a commit message in the destination to refer to a
   * revision. For example "Git-RevId".
   */
  String getLabelName();

  /**
   * If not null, returns a stable name representing the reference from where this {@code Revision}
   * was created.
   *
   * <p>For example if the user passed 'master' in the command line, the {@link #asString()}  would
   * return the SHA-1 and this method would return 'master'. Note that it is a valid response
   * to return {@link #asString()} here if the implementation chooses to.
   */
  default @Nullable String contextReference() {
    return null;
  }

  /**
   * Return any associated label with the revision. Keys are the label name and values are the
   * content of the label.
   */
  default ImmutableMap<String, String> associatedLabels() {
    return ImmutableMap.of();
  }
}
