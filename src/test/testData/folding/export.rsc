<fold text='# 2026-01-01 by RouterOS 7.26 ...'># 2026-01-01 by RouterOS 7.26
# software id = XXXX</fold>
/interface bridge<fold text=' ...'>
add name=bridge1
add name=bridge2</fold>
/ip firewall filter<fold text=' ...'>
<fold text='add action=accept chain=input ...'>add action=accept chain=input \
    comment="defconf"</fold></fold>
:if (true) do=<fold text='{...}'>{
    :put 1
}</fold>
