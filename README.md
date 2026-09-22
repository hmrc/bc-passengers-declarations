# bc-passengers-declarations

This service is used for submission of passengers declarations to MDG.

## Flow

### Successful Flow

* A call is made to the `submit` endpoint with a valid payload.

> This stores a record of the declaration locally in a `PendingPayment` state.

* The user pays.

* A call is made to the `update` endpoint with the relevant `chargeReference`.

> This sets the state of the declaration to `Paid`.

> The `DeclarationSubmissionWorker` which polls MongoDB for `Paid` declarations submits the record to MDG, retrying on error.

* The declaration is submitted to MDG.

### Unpaid Flow

* A call is made to the `submit` endpoint with a valid payload.

> This stores a record of the declaration locally in a `PendingPayment` state.

* *A configurable amount of time* elapses without a matching `update` call.

> The `PaymentTimeoutWorker` which polls MongoDB for `PendingPayment`
records whose `lastUpdated` record is longer ago than *a configurable
amount of time* processes the record.

**Currently this processing just logs the record**

### Failed flow

* A call is made to the `submit` endpoint with a valid payload.

> This stores a record of the declaration locally in a `PendingPayment` state.

* The user pays.

* A call is made to the `update` endpoint with the relevant `chargeReference`.

> This sets the state of the declaration to `Paid`.

> The `DeclarationSubmissionWorker` which polls MongoDB for `Paid` declarations submits the record to MDG, retrying on error.

* The declaration is submitted to MDG but returns a `400 BAD REQUEST`.

> The `DeclarationSubmissionWorker` updates the state of the declaration to `Failed` and stops attempting to process it.

On subsequent starts of the application:

> The `FailedSubmissionWorker` sets the state of all `Failed` declarations to `Paid`

**NOTE: This does not continually run, it only runs on startup of the application.**

> The `DeclarationSubmissionWorker` attempts to submit the declaration to MDG again.

The idea here is that in order for any `Failed` declaration to be
successfully processed there should be a code change.

If a fix is put into the code to compensate for the failure then that will require an application
restart which will allow these records to be processed again.

## Endpoints

The four endpoints below all live under `/bc-passengers-declarations`, so for example `submit-declaration` is really `POST /bc-passengers-declarations/submit-declaration`.

### `POST /submit-declaration`

Creates a new declaration. This is where a passenger's journey starts - before they've paid anything.

#### Request headers

| Header             | Required | Value |
|--------------------|----------|-------|
| `Content-Type`     | Yes      | `application/json` |
| `X-Correlation-ID` | Yes      | Any string. Sent back on the response and stored against the declaration so the request can be traced end to end. |

#### Request body

The declaration payload (see the schema at `conf/schemas/declarationsRequestSchema.json`, or just look at the `Submit Declaration` request in the [Postman collection](#postman-collection) below).

#### Response statuses

| Status | When |
|--------|------|
| `202`  | Accepted. A charge reference has been generated and the declaration is stored in Mongo with state `pending-payment`. |
| `400`  | Either the `X-Correlation-ID` header was missing, or the body failed schema validation. |
| `500`  | Something went wrong on our side - most likely we couldn't reach MongoDB. |

On a `202`, the body is the stored declaration document, which includes the generated `chargeReference` you'll need for the next two calls. On a `400` from failed validation, the body is a JSON array of the individual validation errors, e.g. `"errors": ["object has missing required properties ([\"foo\"])"]`.

### `POST /submit-amendment`

Same idea as `submit-declaration`, but for amending a declaration that's already been submitted (typically because the passenger has more to declare after the fact).

#### Request headers

Same as `submit-declaration` - `Content-Type: application/json` and `X-Correlation-ID` are both required.

#### Request body

The amendment payload, containing the original `chargeReference` plus the revised declaration data.

#### Response statuses

| Status | When |
|--------|------|
| `202`  | Accepted. The amendment is stored against the existing declaration with amend-state `pending-payment`. |
| `400`  | Either the `X-Correlation-ID` header was missing, or the body failed schema validation. |
| `423`  | The declaration is currently locked by another request (e.g. a worker is processing it at the same moment). Safe to retry. |

### `POST /update-payment`

Called once the passenger has actually paid (or the payment failed/was cancelled), to move the declaration on to the next stage.

#### Request body

```json
{
  "reference": "XHPR1234567890",
  "status": "Successful"
}
```

`status` is one of `Successful`, `Failed` or `Cancelled`.

#### Response statuses

| Status | When |
|--------|------|
| `202`  | The declaration's (or amendment's) state has been updated to reflect the payment outcome - `paid`, `payment-failed` or `payment-cancelled`. Calling this again on an already-`paid` declaration is harmless and still returns `202`. |
| `400`  | The request body didn't match the expected shape. |
| `404`  | No declaration exists for that `reference`. |
| `409`  | The declaration is in a state that can't take a payment update right now - for example it's already `Failed`. |
| `423`  | The declaration is locked by another request at the same moment (e.g. the `PaymentTimeoutWorker` is processing it). Safe to retry. |

Once a declaration is marked `paid`, the `DeclarationSubmissionWorker` (or `AmendmentSubmissionWorker` for amendments) picks it up in the background and submits it to MDG/HIP - see the [Flow](#flow) section above.

### `POST /retrieve-declaration`

Looks up a previously submitted declaration by the passenger's last name and their reference number, mainly used for support/lookup purposes.

#### Request body

```json
{
  "lastName": "Doe",
  "referenceNumber": "XHPR1234567890"
}
```

#### Response statuses

| Status | When |
|--------|------|
| `200`  | Found - the response body is the declaration. |
| `400`  | The request body didn't match the expected shape. |
| `404`  | No declaration matches that last name and reference number combination. |

## Postman collection

A Postman collection covering the service's endpoints lives at [`postman/bc-passengers-declarations.postman_collection.json`](postman/bc-passengers-declarations.postman_collection.json).

It contains:

| Request | Endpoint |
|---------|----------|
| `Submit Declaration` | `POST /bc-passengers-declarations/submit-declaration` |
| `Submit Declaration (With Vape - new EPID1778 OAS)` | `POST /bc-passengers-declarations/submit-declaration` |
| `Submit Amendment` | `POST /bc-passengers-declarations/submit-amendment` |
| `Update Payment` | `POST /bc-passengers-declarations/update-payment` |
| `Retrieve Declaration` | `POST /bc-passengers-declarations/retrieve-declaration` |

URLs are hardcoded to `http://localhost:9073`, since Postman is only expected to be run locally against a service started with `sbt run`.

`Submit Declaration` has a test script that captures the returned `chargeReference` into a collection variable, so it can be run before `Update Payment` or `Retrieve Declaration` without manually copying the value across.

To exercise the HIP path (`feature.isUsingHip = true`), also run [`bc-passengers-declarations-stub`](../bc-passengers-declarations-stub) locally (its own `postman/` collection stubs the HIP responses) - see that repo's README for details.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

