# -*- encoding: utf-8 -*-

Gem::Specification.new do |s|
  s.name = "to_regexp"
  s.version = "0.2.1"

  s.required_rubygems_version = Gem::Requirement.new(">= 0") if s.respond_to? :required_rubygems_version=
  s.authors = ["Seamus Abshere"]
  s.date = "2013-05-21"
  s.description = "Provides String#to_regexp, for example if you want to make regexps out of a CSV you just imported."
  s.email = ["seamus@abshere.net"]
  s.homepage = "https://github.com/seamusabshere/to_regexp"
  s.require_paths = ["lib"]
  s.rubyforge_project = "to_regexp"
  s.rubygems_version = "1.8.25"
  s.summary = "Provides String#to_regexp"

  if s.respond_to? :specification_version then
    s.specification_version = 4

    if Gem::Version.new(Gem::VERSION) >= Gem::Version.new('1.2.0') then
      s.add_development_dependency(%q<ensure-encoding>, [">= 0"])
      s.add_development_dependency(%q<yard>, [">= 0"])
    else
      s.add_dependency(%q<ensure-encoding>, [">= 0"])
      s.add_dependency(%q<yard>, [">= 0"])
    end
  else
    s.add_dependency(%q<ensure-encoding>, [">= 0"])
    s.add_dependency(%q<yard>, [">= 0"])
  end
end
