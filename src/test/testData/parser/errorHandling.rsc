:do { :put 1 } on-error={ :log error "x" }
:onerror e in={ :error "boom" } do={ :put $e }
