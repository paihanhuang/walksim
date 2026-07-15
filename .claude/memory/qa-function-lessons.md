# qa-function lessons

- 2026-07-15: osmdroid 6.1.20 tap-parity — `Polyline.infoWindow = null` does NOT stop tap-consume;
  `Polyline.onClickDefault` returns true unconditionally. Verify overlay tap-consume claims against the
  pinned lib's bytecode (`javap -c`), never trust the code comment. Fix is `setOnClickListener{_,_,_->false}`.
