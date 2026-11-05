/interface monitor-traffic $i once do={ :if ($"received-bits-per-second" > 0) do={ :put 1 } }
