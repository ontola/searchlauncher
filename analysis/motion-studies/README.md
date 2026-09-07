# Launcher motion studies

Code-rendered mockups, not recordings of the Android app. App implementation is unchanged.

Every 8-second video compares normal speed (left) with four-times slower transitions (right). Pauses are equal so motion can be compared directly. Videos use 30 fps; the proposed Android implementation would use the display frame clock.

- A: results enter with a 140 ms cubic ease-out fade and 4 px upward travel.
- B: results enter with a 180 ms cubic ease-out fade, 8 px upward travel, and a 16 ms stagger starting at the first match near the keyboard.
- C: full-screen panel enters over 160 ms with a 10 px lift; exits over 140 ms.

Recommendation: A for new results, C for panels. Avoid replaying entry animations on unchanged results during typing. Existing rows and the selection indicator should remain directly responsive to gestures; animations must not postpone publishing or activating results. B is an alternative to evaluate, not an added delay for all result updates.

Re-render with the bundled Python runtime (Pillow) and ffmpeg using render.py.
