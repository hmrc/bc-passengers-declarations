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
successfully processed there must be a code change.

If a fix is put into the code to compensate for the failure then that will require an application
restart which will allow these records to be processed again.

## Endpoints

### `POST /bc-passengers-declarations/submit`

#### Request Headers

| Header         | Value              |
|----------------|--------------------|
| `Content-Type` | `application/json` |

#### Request body

Json payload to be sent to MDG.

#### Response statuses

| Status | Meaning |
|--------|---------|
| `202`  | The request has been accepted for processing. |
| `400`  | The request has failed schema validation. |
| `500`  | Any server error will cause this status, most likely issue would be difficulty in connecting to MongoDB. |

#### Response body (202)

The successful response body is json document which has been stored into mongo.

This will include the following fields:

| Field | Example | Value |
|-------|---------|-------|
| `_id`             | `"XHPR1234567890"`  | This is the charge reference which has been generated for the request which should be used in the subsequent `update` call. |
| `data`            | `{ ... }`           | This is the data which has been submitted, modified to add the charge reference into the place in the structure which MDG expects it. |
| `lastUpdated`     | ???                 | This is the last updated time of the record, in this case it will have been the time at which the model was created. |
| `state`           | `"pending-payment"` | All newly submitted declarations will have a `PendingPayment` state. |

#### Response body (400)

The bad request response body will contain a list of validation errors. For example:

```$json
"errors": [
  "object has missing required properties ([\"foo\"])"
]
```

### `POST /bc-passengers-declarations/update/:chargeReference`

#### Request

No headers are required, post body should be empty.

The `chargeReference` in the url should match one returned from a previous call to the `submit` endpoint.

#### Response statuses  

| Status | Meaning |
|--------|---------|
| `202`  | This means that the declaration identified by the `chargeReference` will have its `status` set to `Paid`. If a declaration is already in the `Paid` state then this will do nothing but still return `202` |
| `404`  | This means that no declaration for the given charge reference exists in MongoDB. |
| `409`  | This means that the declaration is not in a valid state to be set to `Paid`, for example, if a declaration is in a `Failed` state. |
| `423`  | This means that the declaration is already locked for processing by another part of the system. This should only be able to happen if the `update` is submitted at the same time as the `PaymentTimeoutWorker` is attempting to process the record. |
