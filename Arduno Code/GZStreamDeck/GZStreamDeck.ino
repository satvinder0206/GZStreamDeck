#include <Joystick.h>


const int NUM_BUTTONS = 5;
const int NUM_SLIDERS = 5;

const int buttonPins[] = {2, 3, 4, 5, 6};
const int sliderPins[] = {A0, A1, A2, A3, A10};

int lastButtonState[] = {0, 0, 0, 0, 0};
int lastSliderState[] = {0, 0, 0, 0, 0};

const int ADC_NOISE_THRESHOLD = 3;

Joystick_ GZStreamDeck(0x04, JOYSTICK_TYPE_GAMEPAD, 
  NUM_BUTTONS, 0, true, true, true, true, true, false, false, false, false, false, false);

void setup() {
  for (int i = 0; i < NUM_BUTTONS; i++) {
    pinMode(buttonPins[i], INPUT_PULLUP);
  }
  GZStreamDeck.setXAxisRange(0, 1023);
  GZStreamDeck.setYAxisRange(0, 1023);
  GZStreamDeck.setZAxisRange(0, 1023);
  GZStreamDeck.setRxAxisRange(0, 1023);
  GZStreamDeck.setRyAxisRange(0, 1023);
  GZStreamDeck.begin(false); 
}

void loop() {
  bool stateChanged = false;
  for (int i = 0; i < NUM_BUTTONS; i++) {
    int currentButtonState = !digitalRead(buttonPins[i]); 
    if (currentButtonState != lastButtonState[i]) {
      GZStreamDeck.setButton(i, currentButtonState);
      lastButtonState[i] = currentButtonState;
      stateChanged = true;
    }
  }

  for (int i = 0; i < NUM_SLIDERS; i++) {
    int currentSliderState = analogRead(sliderPins[i]);
    if (abs(currentSliderState - lastSliderState[i]) >= ADC_NOISE_THRESHOLD) {
      switch(i) {
        case 0: GZStreamDeck.setXAxis(currentSliderState); break;
        case 1: GZStreamDeck.setYAxis(currentSliderState); break;
        case 2: GZStreamDeck.setZAxis(currentSliderState); break;
        case 3: GZStreamDeck.setRxAxis(currentSliderState); break;
        case 4: GZStreamDeck.setRyAxis(currentSliderState); break;
      }
      lastSliderState[i] = currentSliderState;
      stateChanged = true;
    }
  }
  if (stateChanged) GZStreamDeck.sendState();
  delay(10); 
}