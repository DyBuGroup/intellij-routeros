:foreach i in=[/interface wireless find] do={
  if ($bFirst = 1) do={
    local name [/interface wireless get $i name]
    put $name
  } else={
    delay 1s
  }
}
/ip dns {
  set allow-remote-requests=yes
  iface add name=x
}
do {
  return 1
} on-error={ error "failed" }
