/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc

import org.asynchttpclient.Request

object DispatchCrossSupport {
  // Dispatch 0.11.4 pulls in ning / async-http-client. This was broken out into it's own project
  // under async-http-client. That updated library is pulled in by dispatch 0.13.4. To work around this, we
  // use a type alias to the correct type, depending on the sbt version being cross-compiled. See
  // https://jira.tools.tax.service.gov.uk/browse/BDOG-510 for more context.
  type Response = org.asynchttpclient.Response

  // Similarly, resolve the Http object to the version specific variant
  val Http = dispatch.Http.default

  // Used in tests
  def extractRequestHeader(request: Request, headerName: String): String = request.getHeaders.get(headerName)
}
