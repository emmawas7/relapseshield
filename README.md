# Relapse Shield

Relapse Shield is a follow-up app for people who have left a rehabilitation centre. A patient does a short check-in each day, covering mood, how strong the cravings are, and whether anything triggered them. The counsellor who treated them sees on one screen which patients need attention first. If a patient is in trouble, the app has an emergency screen with a guided breathing exercise, a one-tap dialler, and an urgent alert that goes straight to their counsellor.

- **Course**: CSC3109 Mobile Applications Development, BCS III, Mbarara University of Science and Technology
- **Student**: Wasswa Emmanuel (2024/BCS/225/PS)
- **Repository Tags**: `m2`, `m3`, `m4`

## 1. Setup
- Android Studio Quail 4 (2026.1.4), Java, Empty Views Activity project.
- `minSdk 24` (Android 7.0), `targetSdk 37`. API 24 is the level used in the course setup, and Android 7.0 and above covers nearly every phone still in use.
- Backend: Firebase Authentication (email and password) and Cloud Firestore, added through the Firebase BoM (version 35.1.0) so the library versions stay matched.
- Tested on an Android Studio emulator and on a physical Samsung SM-G991U1 running Android 15.
- Keys: `google-services.json` is not in the repository, because it is listed in `.gitignore`. To run the project, create a Firebase project for the package name `com.example.relapseshield`, switch on Email/Password sign-in and Firestore, and put your own `google-services.json` in the app folder.

## 2. What each milestone contains
- **Milestone 1**: concept and design, the Welcome, Login and Register screens, input validation, welcome feedback, and lifecycle logging on Login.
- **Milestone 2**: navigation between screens with Intents, counsellor linking by code, the counsellor dashboard sorted by risk, menus and dialogs, calls and messages through the phone's own apps, and a reusable list row.
- **Milestone 3**: the move to Cloud Firestore (create, read, update and delete), behaviour with no connection, role-based access, edit and delete for check-ins with confirmation, account deletion, flag words, faster loading, and the security and privacy controls.
- **Milestone 4**: a REST API screen (`ResourcesActivity`), a daily reminder using exact alarms, a test log with ten cases, a bug log, and a stack comparison.

## 3. Users and personas

| User | What they do in the app |
|---|---|
| **Patient** | Registers with the code from their own counsellor, gives next-of-kin details, saves daily check-ins, edits or deletes them, uses the Emergency screen, sets a daily reminder, and can delete their own account |
| **Counsellor** | Confirms the patients who ask for them, sees an alert-first dashboard, manages their own flag words, calls or messages a patient, and marks check-ins as reviewed |

### Personas

#### Persona 1: Nabakooza Bridget Maria (patient)
- **Age**: 24. She left an outpatient addiction programme three weeks ago, lives on her own and works.
- **Goal**: finish a daily one-minute reflection without feeling overwhelmed, and reach help quickly if cravings spike.
- **Frustration**: she forgets the daily habit, gets hit by sudden triggers, and fears a relapse before her counsellor knows anything is wrong.

#### Persona 2: Dr. Emmanuel Wasswa (counsellor)
- **Age**: 42. A senior addiction counsellor who follows up outpatients between clinical rounds.
- **Goal**: spot at a glance which patients have a falling mood, strong cravings or have gone quiet, so he can step in before a relapse.
- **Frustration**: scattered paper notes, and hearing about problems only when a patient or relative calls after a crisis.

## 4. Screens and design

| Screen | Class | Layouts | Main controls | Purpose |
|---|---|---|---|---|
| **Welcome** | `WelcomeActivity` | `ScrollView`, `LinearLayout` | Buttons | Explains the app and its privacy promise. Goes straight in if the user chose Remember me |
| **Login** | `LoginActivity` | `ScrollView`, `ConstraintLayout` | `TextInputLayout`, `CheckBox` | Signs in, remembers the email, and offers a password reset |
| **Register** | `RegisterActivity` | `ScrollView`, `LinearLayout` | `RadioButton`, `Spinner`, `CheckBox` | Creates a patient account (with counsellor code and next of kin) or a counsellor account (which gets its own code) |
| **Connect with counsellor** | `LinkActivity` | `LinearLayout` | `TextInputLayout` | Lets a patient who is not linked yet enter a counsellor's code and see the request status |
| **Home (patient)** | `HomeActivity` | `LinearLayout`, `ListView` | `ArrayAdapter`, `PopupMenu`, `AlertDialog` | The patient's check-ins, New check-in, Emergency, and the menu |
| **New or edit check-in** | `CheckinActivity` | `ScrollView`, `LinearLayout` | `Slider`, `RadioGroup`, `CheckBox` | Mood 1 to 5, cravings, felt triggered, and a note that is checked for flag words |
| **Emergency** | `EmergencyActivity` | `ScrollView`, `LinearLayout` | Buttons, `CountDownTimer` | One-minute breathing exercise, dialler, and an urgent alert to the counsellor |
| **Dashboard (counsellor)** | `DashboardActivity` | `LinearLayout`, `ListView` | `ArrayAdapter`, `PopupMenu`, `AlertDialog` | Open alerts first, then requests, then patients in order of risk |
| **Patient (counsellor)** | `PatientActivity` | `ScrollView`, `LinearLayout` | Buttons | One patient's contact details, next of kin and recent check-ins, with Call, Message, Call next of kin and Mark reviewed |
| **Flag words** | `WordsActivity` | `ScrollView`, `LinearLayout`, `ListView` | `RadioGroup`, `TextInputEditText` | A counsellor adds and removes their own "watch" and "urgent" words |
| **Help and resources** | `ResourcesActivity` | `LinearLayout`, `ListView` | `ProgressBar`, Button | Centres and helplines loaded over REST |
| **Daily reminder** | `ReminderActivity` | `ScrollView`, `LinearLayout` | `SwitchCompat`, `TimePickerDialog` | Turns the daily reminder on or off, changes its time, and sends a test reminder |

- **Why these layouts**: Every form sits in a `ScrollView`, so nothing is cut off when the keyboard is open, when the text size is increased or when the phone is turned sideways. Login uses a `ConstraintLayout` because its fields and links line up against each other. The longer forms use vertical `LinearLayout`s, which are predictable when a block appears or disappears, for example the patient-only fields on Register. Home, the dashboard and the resources list use a `ListView` with an adapter, because the number of rows changes as data arrives.
- **Orientation and screen sizes**: The screens scroll and use `match_parent` and weighted widths instead of fixed sizes, so they work in portrait and landscape on different screen sizes. Android restores the text in fields that have an ID, and Emergency also saves whether the breathing exercise is finished, so rotating the phone does not lock the alert button again.
- **Accessibility**: Touch targets are at least 48dp high. Text uses `sp`, so it follows the phone's font size, and spacing uses `dp`. Text colour on the background has strong contrast, the logo has a content description, and each field has a hint or label. Errors appear next to the field they belong to.
- **Keyboard, menu and buttons**: After testing I changed three things so people do not have to guess. The screens shrink above the keyboard so the box being typed in stays visible. The Menu label has a small down arrow, and check-in rows have a chevron. Tapping a check-in opens a pop-up with real Edit, Delete and Close buttons, instead of hiding the options in a long press.
- **Trust wording**: The Welcome screen says that a patient's information is shared only with their own counsellor and is stored in Google's cloud.

## 5. Design decisions
- **A Welcome screen that explains before it asks**: It answers "what is this, is it for me, can I trust it" before anyone is asked for details.
- **Firestore instead of a list kept in the app**: My first version stored users in memory, which was enough to build Login and Register. That cannot be shared between a patient's phone and a counsellor's phone, so I moved to Cloud Firestore.
- **The counsellor is a real user of the app**: The counsellor sees flags as they happen, which moves follow-up from reacting to a crisis to noticing early signs.
- **Patients use their own doctor's code**: A patient can only link to the person who treated them, no one can pick a stranger from a list, and the counsellor must confirm the request.
- **Only counsellors can call or message**: This keeps the clinical boundary clear and stops the buttons becoming a way to pester a counsellor.
- **Counsellor accounts start unapproved**: Anyone can register as a counsellor, but nobody sees patient data until an administrator approves the account.
- **A breathing step before the alert button, but never before the dialler**: The minute gives the patient a moment to settle. In real danger nobody should wait, so the call button is always available.

## 6. Data model (Cloud Firestore)

| Collection | Key fields | Purpose |
|---|---|---|
| `users` | `role`, `name`, `phone`, `homeArea`, `kinName`, `kinRelation`, `kinPhone`, `counsellorId`, `code`, `approved`, `clientCount`, `lastReviewedAt`, `assignedAt` | Profiles for patients and counsellors |
| `counsellorCodes` | `counsellorId`, `counsellorName` | Matches each six-character code to its counsellor |
| `requests` | `patientId`, `patientName`, `patientPhone`, `counsellorId`, `counsellorName`, `status`, `createdAt` | A patient's request, waiting for the counsellor to confirm |
| `checkins` | `userId`, `counsellorId`, `mood`, `cravings`, `triggered`, `note`, `urgentWords`, `watchWords`, `reviewed`, `createdAt`, `editedAt` | Daily check-ins |
| `alerts` | `patientId`, `patientName`, `counsellorId`, `counsellorName`, `status`, `source`, `words`, `createdAt`, `handledAt` | Urgent alerts from the Emergency screen or from urgent words in a note |
| `accessLog` | `counsellorId`, `patientId`, `action`, `createdAt` | Records when a counsellor opens, calls or messages a patient |
| `flagWords` | `counsellorId`, `word`, `level` (watch or urgent) | Each counsellor's own word list |
| `resources` | `name`, `type`, `phone`, `district`, `notes` | Public centres and helplines, loaded by the REST screen. Edited by the administrator in the console |

## 7. How a patient is flagged
`FlagRules.java` holds the rules in one place. A patient is flagged when their latest unreviewed check-in shows any of these: mood of 2 or lower, cravings at High, or "felt triggered" together with cravings at Medium or High. A patient is also flagged after three or more days without a check-in. The three days are counted from the last check-in, or from the day they were confirmed or last reviewed.

Flag words. Each counsellor keeps their own list. A watch word marks a check-in for attention, and an urgent word raises an alert for the counsellor straight away and shows the patient a help message. The patient's note is compared with their own counsellor's list when the check-in is saved, so a matching word becomes part of the record. If the word list cannot be loaded, the check-in is still saved, just without word matching.

The dashboard shows open alerts first, then pending requests, then patients with the most flags first and the rest in alphabetical order. Marking a patient as reviewed clears the flags for the check-ins the counsellor has seen.

## 8. Validation rules
- **Login**: email required and in a valid format ("Enter your email address", "Enter a valid email address"). Password required and at least 6 characters. A wrong email and a wrong password give the same message, "Incorrect email or password", so the app does not reveal which emails have accounts.
- **Register**:
  - Full name: at least 2 characters.
  - Email: valid format. Firebase reports an email that is already in use.
  - Phone: `07XXXXXXXX` or `+2567XXXXXXXX`.
  - Patients also give a home area, next of kin details and a counsellor code. A wrong code gives "That code was not found".
  - Password: at least 8 characters with a letter and a digit, and the confirmation must match.
  - The consent box must be ticked.
- **Check-in**: mood is always between 1 and 5. Cravings start on None, so one choice is always selected. A note over 500 characters is refused with a message.
- **Forgot password**: the email must be valid. The reply is the same whether or not an account exists.

## 9. Navigation and Intents
- **Explicit Intents**: move between the app's own screens. Extras carry user IDs, names, the role and check-in IDs.
- **Back Stack Management**: After login and logout, `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` clears the back stack, so Back leaves the app instead of cycling through login screens.
- **Role-based auto-navigation**: `WelcomeActivity` checks the saved sign-in and the saved role. A counsellor who chose Remember me goes straight to the dashboard and a patient straight to Home.
- **Implicit Intents**: hand work to the phone's own apps. `ACTION_DIAL` opens the dialler with the number filled in, and `ACTION_SENDTO` with an `smsto:` address opens the messaging app with a greeting. The user presses the green button or Send. The app never makes the call itself, so it needs no call or SMS permission.

## 10. Storage and behaviour with no connection

Patient and counsellor data is in Cloud Firestore, so it survives closing the app and is shared between two phones. A small amount is kept on the phone in `SharedPreferences`: whether Remember me is ticked, the last email used, the reminder settings, and the user's name and role so the correct screen opens quickly. No password is ever stored.

### What happens without a connection

| Part of the app | Behaviour |
|---|---|
| **Login and Register** | These need a connection, because Firebase Authentication checks the account online. With none, the app shows "No internet connection" and does not crash |
| **Home** | The list uses a live Firestore listener, which shows what is already saved on the phone straight away and updates when changes arrive |
| **Counsellor dashboard** | It asks the server for fresh data first. If that fails, it falls back to the copy saved on the phone, so a counsellor with a poor signal still sees the last known list |
| **Saving a check-in** | Firestore queues changes made offline and sends them when the connection returns. The Save button waits for the server to confirm, so offline it stays disabled until the connection is back. I have not added a message for this, and I list it as a limitation |
| **Help and resources** | This screen reads from the internet and shows a clear "No internet connection" message with a Try again button (test case TC-09) |
| **Emergency dialler** | This works with no data at all, because it only opens the phone's dialler. Someone in danger is never blocked by a missing signal |

## 11. Security and privacy

| Risk | Control | Where |
|---|---|---|
| **Any signed-in user reads other people's health data** | Firestore security rules: a patient reads and writes only their own records, and a counsellor reads only the records of their own linked patients | Firestore rules (kept in the Firebase console) |
| **Someone claims to be a counsellor** | Counsellor accounts start unapproved (`approved: false`) and see nothing until an administrator approves them | `RegisterActivity`, `DashboardActivity`, rules |
| **A user changes their own role or counsellor** | The rules refuse client updates to role, approved and counsellorId | Firestore rules |
| **A counsellor takes on too many patients** | The limit of 10 is enforced in an atomic Firestore transaction when a request is accepted | `DashboardActivity` |
| **A counsellor misuses contact details** | Every view, call and message is written to `accessLog` with the time | `PatientActivity` (`logAccess`) |
| **Passwords exposed** | Handled only by Firebase Authentication. The app never stores a password, and Remember me stores only an email and a yes or no flag | `LoginActivity`, `RegisterActivity` |
| **Finding out who has an account** | One message for a wrong email or password, and one reply for a password reset | `LoginActivity` |
| **Someone deletes an account from an unlocked phone** | "Delete my account" asks for the password again before anything is removed | `HomeActivity` |
| **Private information on a locked screen** | The reminder shows only a neutral "Reminder" on the lock screen | `ReminderReceiver` |
| **Public data mixed with private data** | The resources collection holds only public centres and helplines, so it can be read without signing in, and it cannot be written from the app | Firestore rules |
| **Keys in the repository** | `app/google-services.json` is listed in `.gitignore` | `.gitignore` |
| **Unneeded permissions** | No call or SMS permission is requested. The only permissions are for notifications, exact alarms, restart handling and vibration, all used by the reminder | `AndroidManifest.xml` |

- **Personal data collected, and why**: Name and email identify the account. Phone numbers let a counsellor call or message a patient. Home area helps the counsellor if a patient needs to be found. Next of kin details give someone to contact in an emergency. Check-in answers are the purpose of the app. The data is sent over an encrypted connection to Google's cloud (Firebase), and the consent box and About screen say so. A patient can delete their account and all their data from the menu.

### Administrator procedure: removing a patient completely
This is done by hand in the Firebase console, in this order:
1. **Authentication, Users**: copy the patient's User UID, then delete the account.
2. **Firestore, users**: delete the document with that UID.
3. **Firestore, requests and alerts**: delete the document with that UID, if there is one.
4. **Firestore, checkins**: filter `userId` equal to the UID and delete each one.
5. **Firestore, users (the counsellor's document)**: lower `clientCount` by 1.
6. On the counsellor's dashboard, open the menu and tap Refresh to confirm the patient is gone.

If a profile is deleted but the login is not, the app signs the person out and shows "This account has been removed". I chose a console procedure over an admin screen because removals are rare and a hospital's records staff already use their own systems. The cost is that it depends on someone following the steps, and a missed step such as step 5 leaves a counsellor looking fuller than they are.

## 12. REST API: the Help and resources screen
- **What it does**: The screen sends an HTTP GET request to this project's Firestore REST address for the resources collection: `https://firestore.googleapis.com/v1/projects/<project id>/databases/(default)/documents/resources?pageSize=100`. The reply is JSON. Each entry sits under `documents`, and each field is wrapped in its type, for example `"name": { "stringValue": "..." }`. The app reads name, type, phone, district and notes and shows them as a list. Tapping an entry shows its details, with a Call button that opens the dialler.
- **Off the main thread**: The request runs on a background thread (an `ExecutorService`), never on the main thread. The main thread draws the screen, so a slow network call there would freeze the app, and Android refuses network work on it with a `NetworkOnMainThreadException`. The result goes back to the screen with `runOnUiThread`, and only if the screen still exists, so rotating the phone while loading cannot crash it.
- **Error handling**: The connection waits at most 8 seconds to connect and 8 seconds for data. Each kind of failure shows its own message and a Try again button:

| What goes wrong | How the code catches it | What the user sees |
|---|---|---|
| **No internet** | `UnknownHostException`, `ConnectException`, `IOException` | "No internet connection. Check your data or Wi-Fi and try again." |
| **The server is too slow** | `SocketTimeoutException` | "The server took too long to answer. Please try again." |
| **The server refuses or fails** | `ApiException`, which carries the HTTP status code | "The server returned an error (code 403)..." |
| **The reply cannot be read** | `JSONException` | "The information could not be read. Please try again later." |

- **Where a POST would apply**: A POST to the same address would create a new resource entry, and a patient could one day send feedback the same way. The app does not need it, because the administrator manages the entries in the console, and writing from the app would need authentication and new security rules.

## 13. Daily reminder (device capability and permission)
The Daily reminder screen has a switch, a time picker and a "Send a test reminder now" button.

- **Scheduling**: The reminder uses `AlarmManager` with an exact, battery-aware alarm (`setExactAndAllowWhileIdle`), so it arrives at the chosen time even when the phone is idle. When the alarm fires, `ReminderReceiver` shows the notification and schedules the next day's alarm. `BootReceiver` schedules it again after the phone restarts.
- **Notification**: It uses a high-importance channel with the alarm sound and a vibration pattern, so it is hard to miss. Tapping it opens the app. On a locked screen only a neutral "Reminder" shows.
- **Permission flow**: On Android 13 and above the app must ask before it can show notifications. Turning the switch on shows the system question through `ActivityResultContracts.RequestPermission`. If the user allows it, the reminder is set. If the user refuses, the switch turns itself off, the screen explains that notifications are blocked, and a button opens the phone's notification settings. The app keeps working normally.
- **Exact alarms**: The manifest declares the exact alarm permissions. If Android refuses an exact alarm, the code falls back to a less exact one (`setAndAllowWhileIdle`) instead of crashing.

## 14. Learning log

| Topic | Sources | What I learned | A problem I solved |
|---|---|---|---|
| **Event handling** | Course eBook, Android documentation | A click listener is code the system calls when the user taps. Validation, `setError` and Toast messages all start from listeners | The Message button did nothing after two sets of edits clashed in one file. I logged every tap with a tag and checked in Logcat that the button reached the code, which showed the listener was missing |
| **Activity lifecycle** | Android Developer Guide | `onCreate`, `onStart` and `onResume` run as a screen comes forward, and the reverse as it leaves. Rotation destroys and recreates the screen | Login logs every callback so I could watch rotation destroy and recreate the screen. Emergency saves `exerciseDone` in `onSaveInstanceState`, so a finished breathing exercise survives a rotation |
| **Navigation and Intents** | Android API reference | An explicit Intent names the screen to open and carries extras. An implicit Intent asks the system for any app that can do the job | Counsellors briefly saw the patient screen after login. Welcome and Login now read the saved role first and open the dashboard directly |
| **Lists, menus and dialogs** | Material Design guidelines | An adapter turns data into rows, and the list changes only when the adapter is told | The Menu looked like plain text and the Edit and Delete options were hidden. I added a down arrow, a chevron on each row, and a pop-up with real buttons |
| **Firebase Authentication and Firestore** | Firebase documentation | Authentication proves who the user is, Firestore stores documents in collections, and a `WriteBatch` makes several changes succeed or fail together | The app was slow because screens waited for one request after another. Dashboard queries now run together, and Home uses a live listener that shows saved data first |
| **Security rules** | Firebase documentation | Rules decide who can read and write each document, and they must be written around the real relationships in the data | My first rules let any signed-in user read everything. I rewrote them around the patient and counsellor link. Later, "Could not load patient" errors on review came from updates the rules refused, and I replaced them with batch updates to check-ins and alerts |
| **Web requests and threads** | Android documentation | Network calls must not run on the main thread, and every failure needs its own message | The resources screen catches no connection, a timeout, a server error and unreadable data separately, and each shows a different message |
| **Notifications and permissions** | Android documentation | From Android 13 an app must ask before showing notifications, and the user may say no | The reminder switch turns itself off when permission is refused and offers the settings page, so nothing crashes |

### Other real problems I solved
- The app stayed purple until the theme style matched the Material 3 components.
- The Firebase console had been reorganised, so Authentication and Firestore were under the product categories and not under "Build".
- The first Gradle file listed the Firebase dependencies twice, which was untidy even though it built.
- A Gradle build failed with "Duplicate resources" until I removed the extra `.png` launcher icons that clashed with the `.webp` ones.

## 15. Lifecycle behaviour (Login)
`LoginActivity` writes a log line for each lifecycle callback, using one tag: `onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onRestart`, `onDestroy`, `onSaveInstanceState` and `onRestoreInstanceState`. Filtering Logcat by that tag shows the order below for each action:

| What I did | Callbacks, in order | What it means |
|---|---|---|
| **Launched the app** | `onCreate`, `onStart`, `onResume` | The screen is created, becomes visible, then is ready for input |
| **Pressed the Home button** | `onPause`, `onStop`, `onSaveInstanceState` | The screen is no longer in front, and Android saves its state in case it is destroyed |
| **Returned to the app** | `onRestart`, `onStart`, `onResume` | The screen was only stopped, so it restarts instead of being created again |
| **Rotated the phone** | `onPause`, `onStop`, `onSaveInstanceState`, `onDestroy`, then `onCreate`, `onStart`, `onRestoreInstanceState`, `onResume` | Rotation destroys the screen and builds a new one, which is why state is saved and restored |
| **Moved to Register or Home** | `onPause`, `onStop`, and `onDestroy` when the screen calls `finish()` | The screen is finished and removed from the back stack |

## 16. Test log and bug log

### Test cases

| ID | Area | What I did | Expected result | Result |
|---|---|---|---|---|
| **TC-01** | Login validation | Left email and password empty | "Enter your email address" and "Enter your password" appear next to the fields | Pass |
| **TC-02** | Login | Used an unregistered email, then a wrong password | One message: "Incorrect email or password" | Pass |
| **TC-03** | Counsellor linking | A patient entered a code that does not exist | "That code was not found" appears and no request is created | Pass |
| **TC-04** | Contact | As a counsellor, opened a patient and tapped Message | The phone's messaging app opens addressed to the patient with a greeting filled in | Pass, after the fix in bug 5 |
| **TC-05** | Flag words | A patient saved a note containing an urgent word | The check-in is saved, an urgent alert is created, and a help message is shown | Pass |
| **TC-06** | Daily reminder | Turned the reminder switch on | The alarm is set and the screen confirms the time | Pass |
| **TC-07** | Test reminder | Tapped "Send a test reminder now" | A notification with sound and vibration appears at once | Pass |
| **TC-08** | REST | Opened Help and resources while online | The list loads from the REST address and shows the entries | Pass |
| **TC-09** | REST, no connection | Opened Help and resources in airplane mode | "No internet connection" and a Try again button appear, with no crash | Pass |
| **TC-10** | Delete confirmation | A patient tapped Delete on a check-in | A confirmation appears, and the record is removed only after confirming | Pass |

### Bug log

| No. | Severity | What went wrong | Fix |
|---|---|---|---|
| **1** | Critical | Patients stayed red-flagged after the counsellor reviewed them | `markReviewed()` now updates the check-ins (`reviewed: true`) and the alert (`status: "handled"`) together in one batch, and `FlagRules.reasons()` clears the silence flag once reviewed |
| **2** | Medium | The dashboard Refresh button showed a message without fetching new data | `loadAll()` now asks the server first (`Source.SERVER`), and falls back to the saved copy if that fails |
| **3** | Medium | A counsellor briefly saw the patient screen after login | `WelcomeActivity` and `LoginActivity` read the saved role first and open `DashboardActivity` directly |
| **4** | Medium | The app felt slow | Screens asked for data one request after another. The dashboard now requests everything at once, and Home uses a live listener that shows saved data first |
| **5** | Medium | The Message button did nothing | The click listener had been lost when two sets of edits clashed in one file. I restored it, added a "still loading" message, a second way of opening the messaging app, and a log line for each tap |
| **6** | Medium | The keyboard covered the box being typed in | `ScreenInsets` now shrinks each screen by the keyboard's height, so the field stays visible |
| **7** | Low | The Menu did not look like a dropdown | Added a small down arrow beside the Menu label |
| **8** | Low | Edit and Delete on check-ins were easy to miss | Rows now have a chevron, and tapping one opens a pop-up with Edit, Delete and Close buttons |

## 17. Technology stack comparison (Milestone 4)

I chose native Android in Java and compared it with cross-platform frameworks such as Flutter and React Native.

| Point | Native Android (Java) | Flutter or React Native |
|---|---|---|
| **Speed on low-cost phones** | The code runs directly on Android, with no extra layer | Good, but each framework ships its own engine or runtime, which adds size and work |
| **Device features** | Alarms, notification channels, receivers, runtime permissions and intents are available directly | These features usually need community plugins, and when a plugin lags behind a new Android version, native code is still needed |
| **Platforms** | Android only | One codebase can target Android and iOS |
| **Effort and upkeep** | An iPhone version would mean a second app | Less work to cover both platforms |
| **Fit for this app** | Strong, because the app depends on exact alarms, notifications, the dialler and the messaging app | Possible, but the platform-specific parts would need extra care |

- **My choice**: Native Android suits this app, because the reminder, the emergency dialler, the contact buttons and the permission handling all rely on deep Android features, and I already knew the platform from the course. The cost is that there is no iPhone version.

## 18. Known limitations
- Counsellor approval is set by hand in the Firebase console (`approved: true`).
- Anyone can choose "counsellor" when registering. A real system would have an administrator create counsellor accounts.
- Reminders are local to the phone. There are no remote push messages (Firebase Cloud Messaging) to a counsellor whose app is closed.
- Offline, the Save button on a check-in waits for the server, so it stays disabled until the connection returns, and there is no message explaining this yet.
- Flag words are matched on the patient's phone when the check-in is saved, not on a server.
- The exact alarm permissions are meant for alarm and calendar apps, and Google Play restricts them, so a store release would need to review this choice.
- Android backup is switched on in the manifest with the default rules, so the small amount of local data (the remembered email, the reminder settings and the cached name and role) may be included in a backup.
- Data is stored in Google's cloud, which is outside Uganda, and the consent text says so.
- Removing a patient is a manual console procedure, and the access log keeps only IDs afterwards.
- All demo accounts and patients are fictitious.

## 19. AI tools used
I used two AI tools:
- **Claude AI**: Helped write and explain initial code for registration, the check-in form and list, the counsellor dashboard, flag words, the Help and resources screen, the daily reminder, and speed and keyboard fixes.
- **Gemini (Google AI Assistant in Android Studio)**: Helped debug Firestore batch permissions, build the exact-alarm reminder, set up server-first queries, fix adaptive launcher icons, and debug code. I ran and tested the code on an emulator and physical phone.
