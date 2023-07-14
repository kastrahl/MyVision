# MyVision
 Major Project for final year project submission

Objective -
Make an android app opens and closes by google voice assistant 
Thus making Smartphone another eyes for a blind or partially blind user.
The app must run on low-end devices thus utilising tensorflowLite Model and limited labels.
The app must work offline - Model is shipped with TFlite model itself.

Problems Encountered - 
Integration of google assistant not possible until application is deployed on google playstore.
Image labels are put in queue and object is narrated for detection even when object is out of frame.

Initial Idea -
The Android application uses TensorFlow lite model to label objects detected in frames captured, 
The label is then stored in a queue and passed to Text-To-Speech Engine to read them out aloud

Usage - 
Blind person opens the app using voice command " Ok google open the vision app "
Vision app narrates what it sees in the direction camera is pointed

Future Work -
Fix queue Issue,
Fix number of items detected by using a custom TFLite model instead of MobileNetV1
Describe Scenery then distance to a particular object instead of the object alone.

Feature Idea -
Vision app first describes the scenery and object in it " a room with three people , one standing , two sitting "
User can ask the Vision app questions " Did you see a water bottle in room "
User can ask application approximate Distance to object. 