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

import dispatch.Http
import com.ning.http.client.Request

object DispatchCrossSupport {
  val http: Http = dispatch.Http

  // Used in tests
  type Response = com.ning.http.client.Response
  def extractRequestHeader(request: Request, headerName: String): String = request.getHeaders.getFirstValue(headerName)
}
